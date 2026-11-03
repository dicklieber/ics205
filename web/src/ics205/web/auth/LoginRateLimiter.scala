package ics205.web.auth

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Duration
import java.util.Locale
import scala.collection.mutable

/** Bounded, process-local limits reserved before password verification, including concurrent requests. */
private[auth] class LoginRateLimiter(
  perUser: Int = 5,
  perPeer: Int = 30,
  window: Duration = Duration.ofMinutes(1),
  maxEntries: Int = 10000,
  nowNanos: () => Long = () => System.nanoTime()
):
  require(perUser > 0 && perPeer > 0 && !window.isNegative && !window.isZero && maxEntries >= 2)
  private case class Bucket(start: Long, attempts: Int)
  private val buckets = mutable.Map.empty[String, Bucket]
  private val windowNanos = window.toNanos

  /** None reserves an attempt; Some(seconds) rejects it without extending the cooldown. */
  def acquire(peer: String, username: Option[String]): Option[Long] = synchronized {
    val now = nowNanos()
    buckets.filterInPlace((_, bucket) => now - bucket.start < windowNanos)
    val account = username.map { name =>
      val normalized = name.trim.toLowerCase(Locale.ROOT)
      val digest = MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8))
      "user:" + java.util.HexFormat.of().formatHex(digest)
    }
    val keys = Seq("peer:" + peer -> perPeer) ++ account.map(_ -> perUser)
    val waits = keys.flatMap { (key, limit) =>
      buckets.get(key).filter(_.attempts >= limit).map(bucket => windowNanos - (now - bucket.start))
    }
    if waits.nonEmpty then Some(seconds(waits.max))
    else if buckets.size + keys.count((key, _) => !buckets.contains(key)) > maxEntries then
      // Never evict live entries: rotating usernames must not reset another account's limit.
      Some(seconds(windowNanos))
    else
      keys.foreach { (key, _) =>
        val previous = buckets.getOrElse(key, Bucket(now, 0))
        buckets.update(key, previous.copy(attempts = previous.attempts + 1))
      }
      None
  }

  private def seconds(nanos: Long): Long = math.max(1L, (nanos + 999999999L) / 1000000000L)
