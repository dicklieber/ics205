package ics205.web.auth

import java.time.Duration

class LoginRateLimiterTests extends munit.FunSuite:
  test("account limits apply across peers and case variations without extending the cooldown"):
    var now = 0L
    val limiter = new LoginRateLimiter(perUser = 2, perPeer = 10, nowNanos = () => now)
    assertEquals(limiter.acquire("peer1", Some(" Alice ")), None)
    assertEquals(limiter.acquire("peer2", Some("ALICE")), None)
    assertEquals(limiter.acquire("peer3", Some("alice")), Some(60L))
    now = Duration.ofSeconds(30).toNanos
    assertEquals(limiter.acquire("peer4", Some("alice")), Some(30L))
    now = Duration.ofMinutes(1).toNanos
    assertEquals(limiter.acquire("peer1", Some("alice")), None)

  test("peer limit covers rotating accounts and malformed requests"):
    val limiter = new LoginRateLimiter(perUser = 5, perPeer = 2)
    assertEquals(limiter.acquire("peer1", Some("alice")), None)
    assertEquals(limiter.acquire("peer1", None), None)
    assert(limiter.acquire("peer1", Some("bob")).isDefined)
    assertEquals(limiter.acquire("peer2", Some("bob")), None)

  test("capacity is bounded without evicting live account limits and recovers after expiry"):
    var now = 0L
    val limiter = new LoginRateLimiter(perUser = 1, maxEntries = 2, nowNanos = () => now)
    assertEquals(limiter.acquire("peer1", Some("alice")), None)
    assert(limiter.acquire("peer2", Some("bob")).isDefined)
    assert(limiter.acquire("peer1", Some("alice")).isDefined)
    now = Duration.ofMinutes(1).toNanos
    assertEquals(limiter.acquire("peer2", Some("bob")), None)

  test("concurrent requests cannot exceed the reserved account budget"):
    val limiter = new LoginRateLimiter(perUser = 5)
    val executor = java.util.concurrent.Executors.newFixedThreadPool(8)
    val start = new java.util.concurrent.CountDownLatch(1)
    try
      val requests = (1 to 20).map { n =>
        executor.submit(new java.util.concurrent.Callable[Boolean] {
          def call(): Boolean =
            start.await()
            limiter.acquire(s"peer$n", Some("alice")).isEmpty
        })
      }
      start.countDown()
      assertEquals(requests.count(_.get(10, java.util.concurrent.TimeUnit.SECONDS)), 5)
    finally executor.shutdownNow()
