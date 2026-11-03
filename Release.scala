//| scalaVersion: 3.7.4
//| mvnDeps: [com.lihaoyi::os-lib:0.11.6]

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Properties
import java.util.jar.JarFile
import java.security.MessageDigest

/** Run from the repository root: ./mill --no-daemon Release.scala */
object Release {
  def main(args: Array[String]): Unit = {
    if (args.toSeq == Seq("--help")) {
      println("Usage: ./mill --no-daemon Release.scala\nInteractively build and publish a GitHub release.")
      return
    }
    require(args.isEmpty, "No arguments expected (or use --help).")
    val root = os.pwd
    def check(ok: Boolean, message: => String): Unit =
      if (!ok) throw new IllegalStateException(message)
    def capture(command: String*): os.CommandResult =
      os.proc(command).call(cwd = root, check = false, stderr = os.Pipe)
    def run(command: String*): Unit = {
      os.proc(command).call(cwd = root, stdin = os.Inherit, stdout = os.Inherit, stderr = os.Inherit)
      ()
    }
    def prompt(label: String, default: String): String = {
      val answer = scala.io.StdIn.readLine(s"$label [$default]: ")
      check(answer != null, "Input closed; release cancelled.")
      if (answer.isEmpty) default else answer
    }
    val versionFile = root / "version.txt"
    check(os.isFile(versionFile), "Cannot find version.txt; run from the repository root.")
    check(capture("git", "rev-parse", "--is-inside-work-tree").exitCode == 0, "Not in a Git repository.")
    check(capture("gh", "auth", "status").exitCode == 0,
      "GitHub CLI must be installed and authenticated. Run: gh auth login")
    check(capture("git", "remote", "get-url", "origin").exitCode == 0, "Git remote 'origin' is not configured.")
    val status = capture("git", "status", "--porcelain")
    check(status.exitCode == 0 && status.out.text().isEmpty, "Git working tree is not clean.")

    val originalVersion = os.read(versionFile)
    val current = originalVersion.replaceAll("\\s", "")
    val versionPattern = raw"([0-9]+)\.([0-9]+)\.([0-9]+)(-SNAPSHOT)?".r
    val defaultRelease = current match {
      case versionPattern(major, minor, patch, snapshot) =>
        if (snapshot == null) {
          println(s"Previous release left version $current; starting a new release.")
          s"$major.$minor.${BigInt(patch) + 1}"
        } else s"$major.$minor.$patch"
      case _ => throw new IllegalStateException(s"version.txt must contain X.Y.Z or X.Y.Z-SNAPSHOT; found '$current'.")
    }
    val release = prompt("Release version", defaultRelease)
    check(release.matches(raw"[0-9]+\.[0-9]+\.[0-9]+"), "Release version must be X.Y.Z.")
    val parts = release.split("\\.")
    val next = prompt("Next version", s"${parts(0)}.${parts(1)}.${BigInt(parts(2)) + 1}-SNAPSHOT")
    check(next.matches(raw"[0-9]+\.[0-9]+\.[0-9]+-SNAPSHOT"), "Next version must be X.Y.Z-SNAPSHOT.")
    val tag = s"v$release"
    val artifact = root / s"ics205-$release.jar"
    val checksum = root / s"${artifact.last}.sha256"
    val localTag = capture("git", "show-ref", "--verify", "--quiet", s"refs/tags/$tag")
    check(localTag.exitCode == 1, s"Local tag $tag already exists, or local tags could not be checked.")
    val remoteTag = capture("git", "ls-remote", "--tags", "origin", s"refs/tags/$tag")
    check(remoteTag.exitCode == 0, "Cannot read Git remote. Check authentication (gh auth login; gh auth setup-git).")
    check(remoteTag.out.text().trim.isEmpty, s"Remote tag $tag already exists.")
    run("git", "push", "--dry-run", "origin", "HEAD")
    check(capture("gh", "release", "view", tag).exitCode != 0, s"GitHub Release $tag already exists.")
    check(!os.exists(artifact) && !os.exists(checksum), "Release artifact or checksum already exists.")
    println(s"\nCurrent version : $current\nRelease version : $release\nNext version    : $next\nTag             : $tag\nArtifact        : ${artifact.last}")
    println("Build steps     : clean, __.test, web.assembly (isolated temporary Mill output directory)\n")
    if (!prompt("Release?", "N").equalsIgnoreCase("y")) {
      println("Release cancelled.")
      return
    }

    // A separate output directory avoids a nested Mill lock and guarantees fresh BuildInfo.
    val buildOutput = os.temp.dir(prefix = "ics205-release-")
    var committed = false
    var succeeded = false
    def mill(target: String): Unit = {
      os.proc("./mill", "--no-daemon", target).call(
        cwd = root, env = Map("MILL_OUTPUT_DIR" -> buildOutput.toString),
        stdin = os.Inherit, stdout = os.Inherit, stderr = os.Inherit)
      ()
    }
    try {
      os.write.over(versionFile, s"$release\n")
      println("\nCleaning...")
      mill("clean")
      val started = Instant.now().truncatedTo(ChronoUnit.SECONDS)
      println("\nRunning tests...")
      mill("__.test")
      println("\nBuilding...")
      mill("web.assembly")
      val jarPath = buildOutput / "web" / "assembly.dest" / "out.jar"
      check(os.isFile(jarPath), s"Build succeeded, but artifact was not found: $jarPath")
      val jar = new JarFile(jarPath.toIO)
      val properties = new Properties()
      try {
        val entry = jar.getJarEntry("ics205/BuildInfo.buildinfo.properties")
        check(entry != null, "Assembly is missing BuildInfo properties.")
        val stream = jar.getInputStream(entry)
        try properties.load(stream) finally stream.close()
      } finally jar.close()
      def prop(key: String): String = properties.getProperty(key, "")
      val expectedMill = os.read(root / ".mill-version").trim
      check(prop("version") == release, s"BuildInfo version is '${prop("version")}', expected '$release'.")
      check(prop("millVersion") == expectedMill, s"BuildInfo millVersion is '${prop("millVersion")}', expected '$expectedMill'.")
      check(!Instant.parse(prop("buildTime")).isBefore(started), s"BuildInfo buildTime '${prop("buildTime")}' is older than this release run ($started).")
      println(s"BuildInfo: version=${prop("version")} millVersion=${prop("millVersion")} buildTime=${prop("buildTime")} buildOs=${prop("buildOs")}")
      os.copy(jarPath, artifact)
      val digest = MessageDigest.getInstance("SHA-256")
      val input = java.nio.file.Files.newInputStream(artifact.toNIO)
      try {
        val buffer = new Array[Byte](65536)
        var size = input.read(buffer)
        while (size != -1) {
          digest.update(buffer, 0, size)
          size = input.read(buffer)
        }
      } finally input.close()
      val sha256 = digest.digest().map(b => f"${b & 0xff}%02x").mkString
      os.write(checksum, s"$sha256  ${artifact.last}\n")
      run("git", "add", "version.txt")
      run("git", "commit", "-m", s"Release $release")
      committed = true
      run("git", "tag", "-a", tag, "-m", s"Release $release")
      println("\nPushing release commit and tag...")
      run("git", "push", "origin", "HEAD")
      run("git", "push", "origin", tag)
      println("\nCreating GitHub Release...")
      run("gh", "release", "create", tag, artifact.toString, checksum.toString,
        "--title", release, "--generate-notes")
      os.write.over(versionFile, s"$next\n")
      run("git", "add", "version.txt")
      run("git", "commit", "-m", s"Prepare for $next")
      run("git", "push", "origin", "HEAD")
      succeeded = true
      println(s"\nReleased $release successfully.\nPublished artifact: ${artifact.last}\nDevelopment version: $next")
    } finally {
      try {
        if (!succeeded && !committed) os.write.over(versionFile, originalVersion)
      } finally {
        os.remove(artifact)
        os.remove(checksum)
        os.remove.all(buildOutput)
      }
    }
  }
}
