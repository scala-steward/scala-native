package org.scalanative.testsuite.javalib.nio.file

import java.io._

import java.nio.ByteBuffer
import java.nio.file._
import java.nio.file.attribute._
import java.nio.file.attribute.PosixFilePermission._
import java.nio.file.StandardCopyOption._

import java.util.{Arrays, TreeSet, Collections}
import java.util.EnumSet
import java.util.function.{BiPredicate, IntFunction}

import org.junit.Test
import org.junit.Assert._
import org.junit.Assume._
import org.junit.Ignore

import scala.util.{Try, Failure}

import org.scalanative.testsuite.utils.AssertThrows.assertThrows
import scala.scalanative.junit.utils.CollectionConverters._
import scala.scalanative.junit.utils.AssumesHelper.assumeNotJVMCompliant
import org.scalanative.testsuite.utils.Platform.{isWindows, executingInJVM}

import java.nio.charset.StandardCharsets
import java.nio.file.attribute.PosixFilePermissions

/* See also FilesCopyTest.scala. It provides additional Tests for
 * Files.copy(), including some advanced Tests for use by developer
 * and maintainers.
 */

class FilesTest {
  import FilesTest._

  def assumeShouldTestSymlinks(): Unit = {
    assumeFalse(
      "Do not test symlinks on windows, admin privilege needed",
      isWindows
    )
  }

  @Test def filesCopyCanCopyToNonExistingFile(): Unit = {
    val targetFile = File.createTempFile("test", ".tmp")
    val target = targetFile.toPath()
    assertTrue("!exists || delete()", !targetFile.exists || targetFile.delete())

    val in = new ByteArrayInputStream(Array(1, 2, 3))
    assertEquals("Files.copy()", 3, Files.copy(in, target))

    assertTrue("exists()", targetFile.exists())
    assertEquals("in.read", -1, in.read())

    val fromFile = new FileInputStream(targetFile)
    assertEquals("fromFile.read", 1, fromFile.read())
    assertEquals("fromFile.read", 2, fromFile.read())
    assertEquals("fromFile.read", 3, fromFile.read())
    assertEquals("fromFile.read", -1, fromFile.read())
  }

  @Test def filesCopyThrowsIfTargetExistsAndIsFile(): Unit = {
    val targetFile = File.createTempFile("test", ".tmp")
    val target = targetFile.toPath()
    assertTrue(targetFile.exists() && targetFile.isFile())

    val in = new ByteArrayInputStream(Array(1, 2, 3))
    assertThrows(classOf[FileAlreadyExistsException], Files.copy(in, target))
  }

  @Test def filesCopyThrowsIfTargetExistsAndIsEmptyDir(): Unit = {
    assumeNotJVMCompliant()

    val targetFile = File.createTempFile("test", ".tmp")
    val target = targetFile.toPath()
    assertTrue("delete()", targetFile.delete())
    assertTrue("mkdir()", targetFile.mkdir())
    assertTrue(
      "empty directory",
      targetFile.exists() &&
        targetFile.isDirectory() &&
        targetFile.list().isEmpty
    )

    val in = new ByteArrayInputStream(Array(1, 2, 3))
    assertThrows(classOf[FileAlreadyExistsException], Files.copy(in, target))
  }

  @Test def filesCopyWorksIfTargetExistsAndIsAnEmptyDirAndReplaceExistingIsSet()
      : Unit = {
    val targetFile = File.createTempFile("test", ".tmp")
    val target = targetFile.toPath()
    assertTrue("delete()", targetFile.delete())
    assertTrue("mkdir()", targetFile.mkdir())
    assertTrue(
      "empty directory",
      targetFile.exists() &&
        targetFile.isDirectory() &&
        targetFile.list().isEmpty
    )

    val in = new ByteArrayInputStream(Array(1, 2, 3))
    assertEquals("Files.copy()", 3, Files.copy(in, target, REPLACE_EXISTING))

    assertTrue("isFile()", targetFile.exists() && targetFile.isFile())
    assertEquals("in.read", -1, in.read())

    val fromFile = new FileInputStream(targetFile)
    assertEquals("fromFile.read", 1, fromFile.read())
    assertEquals("fromFile.read", 2, fromFile.read())
    assertEquals("fromFile.read", 3, fromFile.read())
    assertEquals("fromFile.read", -1, fromFile.read())
  }

  @Test def filesCopyThrowsIfTheTargetExistsAndIsNonEmptyDir(): Unit = {
    assumeNotJVMCompliant()

    val targetFile = File.createTempFile("test", ".tmp")
    val target = targetFile.toPath()
    assertTrue("delete()", targetFile.delete())
    assertTrue("mkdir()", targetFile.mkdir())
    assertTrue("isDirectory()", targetFile.exists() && targetFile.isDirectory())
    File.createTempFile("test", ".tmp", targetFile)
    assertTrue("nonEmpty", targetFile.list().nonEmpty)

    val in = new ByteArrayInputStream(Array(1, 2, 3))

    assertThrows(classOf[FileAlreadyExistsException], Files.copy(in, target))
  }

  @Test def filesCopyThrowsIfTargetExistsAndIsNonEmptyDirAndReplaceExistingIsSet()
      : Unit = {
    val targetFile = File.createTempFile("test", ".tmp")
    val target = targetFile.toPath()
    assertTrue("delete()", targetFile.delete())
    assertTrue("mkdir()", targetFile.mkdir())
    assertTrue("isDirectory()", targetFile.exists() && targetFile.isDirectory())
    File.createTempFile("test", ".tmp", targetFile)
    assertTrue("nonEmpty", targetFile.list().nonEmpty)

    val in = new ByteArrayInputStream(Array(1, 2, 3))

    assertThrows(
      classOf[DirectoryNotEmptyException],
      Files.copy(in, target, REPLACE_EXISTING)
    )
  }

  @Test def filesCopyReplacesTheTargetIfExistingFileAndReplaceExistingIsSet()
      : Unit = {
    val targetFile = File.createTempFile("test", ".tmp")
    val target = targetFile.toPath()
    assertTrue("isFile()", targetFile.exists() && targetFile.isFile())

    val in = new ByteArrayInputStream(Array(1, 2, 3))
    assertEquals("Files.copy()", 3, Files.copy(in, target, REPLACE_EXISTING))
    assertEquals("in.read", -1, in.read())

    val fromFile = new FileInputStream(targetFile)
    assertEquals("fromFile.read", 1, fromFile.read())
    assertEquals("fromFile.read", 2, fromFile.read())
    assertEquals("fromFile.read", 3, fromFile.read())
    assertEquals("fromFile.read", -1, fromFile.read())
  }

  @Test def filesCopyCreatesNewDirectory(): Unit = {
    withTemporaryDirectory { dirFile =>
      val targetDir = File.createTempFile("test", "").toPath()
      Files.delete(targetDir)
      Files.copy(dirFile.toPath, targetDir)
      assertTrue("exists()", Files.exists(targetDir))
      assertTrue("isDirectory()", Files.isDirectory(targetDir))
      assertTrue("deleteIfExists()", Files.deleteIfExists(targetDir))
    }
  }

  @Test def filesCopyDoesNotReplaceExistingDirectory(): Unit = {
    withTemporaryDirectory { dirFile =>
      val targetDir = File.createTempFile("test", "").toPath()
      assertThrows(
        classOf[FileAlreadyExistsException],
        Files.copy(dirFile.toPath, targetDir)
      )
    }
  }

  @Test def filesCopyReplacesTheTargetDirectory(): Unit = {
    withTemporaryDirectory { dirFile =>
      val targetDir = File.createTempFile("test", "").toPath()
      Files.delete(targetDir)
      Files.copy(dirFile.toPath, targetDir, REPLACE_EXISTING)
      assertTrue("exists()", Files.exists(targetDir))
      assertTrue("isDirectory()", Files.isDirectory(targetDir))
      assertTrue("deleteIfExists()", Files.deleteIfExists(targetDir))
    }
  }

  @Test def filesCopyDoesNotReplaceNonEmptyTargetDirectory(): Unit = {
    withTemporaryDirectory { dirFile =>
      val targetDir = File.createTempFile("test", "").toPath()
      Files.delete(targetDir)
      Files.copy(dirFile.toPath, targetDir, REPLACE_EXISTING)
      val f = Files.createTempFile(targetDir, "", "")
      assertThrows(
        classOf[DirectoryNotEmptyException],
        Files.copy(dirFile.toPath, targetDir, REPLACE_EXISTING)
      )
    }
  }

  @Test def filesCopyDoesCopySymlinks(): Unit = {
    assumeShouldTestSymlinks()

    withTemporaryDirectory { dirFile =>
      val dir = dirFile.toPath
      val targetFile = Files.createFile(dir.resolve("target"))
      val testString = "test-string"
      Files.write(targetFile, testString.getBytes(StandardCharsets.UTF_8))
      val link = Files.createSymbolicLink(dir.resolve("link"), targetFile)
      assertTrue(Files.isSymbolicLink(link))

      // With NOFOLLOW_LINKS we get a new symbolic link
      val linkCopy =
        Files.copy(link, dir.resolve("link.copy"), LinkOption.NOFOLLOW_LINKS)
      assertTrue(Files.isSymbolicLink(linkCopy))
      assertEquals(targetFile, Files.readSymbolicLink(linkCopy))
      assertEquals(
        testString,
        new String(
          Files.readAllBytes(linkCopy),
          java.nio.charset.StandardCharsets.UTF_8
        )
      )

      // Without NOFOLLOW_LINKS we resolve target file and create copy of a file
      val targetCopy = Files.copy(link, dir.resolve("target.copy"))
      assertFalse(Files.isSymbolicLink(targetCopy))
      assertEquals(
        testString,
        new String(
          Files.readAllBytes(targetCopy),
          java.nio.charset.StandardCharsets.UTF_8
        )
      )
    }
  }

  @Test def filesCopyDoesCopySymlinkDirs(): Unit = {
    assumeShouldTestSymlinks()

    withTemporaryDirectory { dirFile =>
      val dir = dirFile.toPath
      val targetDir = Files.createDirectory(dir.resolve("target-dir"))
      val testFile = "test.file"
      val targetFile = Files.createFile(targetDir.resolve(testFile))
      val testString = "test-string"
      Files.write(targetFile, testString.getBytes(StandardCharsets.UTF_8))
      val link = Files.createSymbolicLink(dir.resolve("link"), targetDir)
      assertTrue(Files.isSymbolicLink(link))

      val linkCopy =
        Files.copy(
          link,
          dir.resolve("dir-link.copy"),
          LinkOption.NOFOLLOW_LINKS
        )
      assertTrue(Files.isSymbolicLink(linkCopy))
      assertTrue(Files.exists(linkCopy.resolve(testFile)))
      assertFalse(Files.isSymbolicLink(linkCopy.resolve(testFile)))
      assertEquals(
        targetDir,
        Files.readSymbolicLink(linkCopy)
      )
      assertEquals(
        testString,
        new String(
          Files.readAllBytes(linkCopy.resolve(testFile)),
          java.nio.charset.StandardCharsets.UTF_8
        )
      )
    }
  }

  @Test def filesCopyShouldCopyAttributes(): Unit = {
    // Incompatible resolution
    assumeNotJVMCompliant()

    withTemporaryDirectory { dirFile =>
      val foo = dirFile.toPath.resolve("foo")
      Files.createFile(foo)
      Files.write(foo, "foo".getBytes)

      if (isWindows) {
        Files.setAttribute(foo, "dos:hidden", Boolean.box(true))
      } else {
        val permissions = Set(OWNER_EXECUTE, OWNER_READ, OWNER_WRITE).toJavaSet
        Files.setPosixFilePermissions(foo, permissions)
      }

      val fooCopy = dirFile.toPath.resolve("foocopy")

      // Issue 2620 - Capture attributes of source file _immediately_before_
      // copying the file. The lastAccessTime of the destination file
      // will be set to the source access time (atime) at this juncture.
      //
      // If the source file attributes are captured after the copy one
      // can get an intermittent assertion failure.
      // If the device (probably /tmp) of the source file is mounted with
      // any time option other than noatime, say relatime, then the act of
      // reading it will change the atime. This later atime will differ
      // from the atime to which the copy has been set. If chance and
      // timing lead to the two atimes to be in different seconds (see below)
      // one gets the valid but unwanted assertion failure.
      //
      // As this test is written, when the file is mounted relatime
      // the first read of the copy would be the first read after the
      // last write to the file (see above). This would change atime.
      // The flow of the mount option strictatime speaks for itself.
      //
      // There is an assumption here that this test has exclusive
      // access to the source file: that is, no other thread or process
      // touches it in this window.

      val attrsCls: Class[_ <: BasicFileAttributes] =
        if (isWindows) classOf[DosFileAttributes]
        else classOf[PosixFileAttributes]

      val attrs = Files.readAttributes(foo, attrsCls)

      Files.copy(foo, fooCopy, COPY_ATTRIBUTES)

      val copyAttrs = Files.readAttributes(fooCopy, attrsCls)

      // Note Well:
      //   If/When attempting to verify the matches below by using
      //   the operating system stat command or equivalent, be
      //   aware that many Scala Native versions truncate
      //   file times to seconds. The operating system may report
      //   microseconds or nanoseconds. The assertions below may
      //   pass when, at first blush, a visual inspection would lead
      //   one to wonder why they were passing.
      //
      //   File times as seconds can lead to other visual anomalies,
      //   such as lastModified times being before birth times. Go figure!

      assertEquals(
        "lastModifiedTime",
        attrs.lastModifiedTime,
        copyAttrs.lastModifiedTime
      )

      assertEquals(
        "lastAccessTime",
        attrs.lastAccessTime,
        copyAttrs.lastAccessTime
      )

      // ctime here is "change time" and should match.
      // ctime is not "birth time", which may differ.
      assertEquals("creationTime", attrs.creationTime, copyAttrs.creationTime)

      (attrs, copyAttrs) match {
        case (attrs: PosixFileAttributes, copyAttrs: PosixFileAttributes) =>
          assertEquals(
            "permissions",
            attrs.permissions.toScalaSet,
            copyAttrs.permissions.toScalaSet
          )
        case (attr: DosFileAttributes, copyAttrs: DosFileAttributes) =>
          assertEquals("isHidden", attr.isHidden(), copyAttrs.isHidden())
        case _ => ??? // unreachable
      }
    }
  }

  @Test def filesCreateSymbolicLinkCanCreateSymbolicLinks(): Unit = {
    withTemporaryDirectory { dirFile =>
      val dir = dirFile.toPath()
      val link = dir.resolve("link")
      val target = dir.resolve("target")
      Files.createSymbolicLink(link, target)
      assertTrue(Files.isSymbolicLink(link))
    }
  }

  @Test def filesCreateSymbolicLinkThrowsIfTheLinkAlreadyExists(): Unit = {
    withTemporaryDirectory { dirFile =>
      val dir = dirFile.toPath
      val link = dir.resolve("link")
      val target = dir.resolve("target")
      link.toFile().createNewFile()
      assertTrue(link.toFile().exists())

      assertThrows(
        classOf[FileAlreadyExistsException],
        Files.createSymbolicLink(link, target)
      )
    }
  }

  @Test def filesExistsReportsExistingFilesAsExisting(): Unit = {
    val targetFile = File.createTempFile("test", ".tmp")
    val target = targetFile.toPath()
    assertTrue("isFile()", targetFile.exists() && targetFile.isFile())

    assertTrue("exists()", Files.exists(target))
    assertTrue("NOFOLLOW", Files.exists(target, LinkOption.NOFOLLOW_LINKS))
  }

  @Test def filesExistsReportsExistingDirectoriesAsExisting(): Unit = {
    val targetFile = File.createTempFile("test", ".tmp")
    val target = targetFile.toPath()
    assertTrue("delete()", targetFile.delete())
    assertTrue("mkdir()", targetFile.mkdir())
    assertTrue("isDirectory()", targetFile.exists() && targetFile.isDirectory())
    assertTrue("exists()", Files.exists(target))
    assertTrue("NOFOLLOW", Files.exists(target, LinkOption.NOFOLLOW_LINKS))
  }

  @Test def filesExistsReportsNonExistingFilesAsSuch(): Unit = {
    val targetFile = File.createTempFile("test", ".tmp")
    val target = targetFile.toPath()
    assertTrue("delete()", targetFile.delete())
    assertFalse("exists()", targetFile.exists())
    assertFalse("!exists()", Files.exists(target))
    assertFalse("NOFOLLOW", Files.exists(target, LinkOption.NOFOLLOW_LINKS))
  }

  @Test def filesExistsHandlesSymlinks(): Unit = {
    assumeShouldTestSymlinks()

    withTemporaryDirectory { dirFile =>
      val dir = dirFile.toPath()
      val existing = new File(dirFile, "existing")
      val nonexisting = dir.resolve("nonexisting")
      val brokenLink = dir.resolve("brokenlink")
      val correctLink = dir.resolve("correctlink")

      existing.createNewFile()
      assertTrue("existing", existing.exists())

      Files.createSymbolicLink(brokenLink, nonexisting)
      Files.createSymbolicLink(correctLink, existing.toPath)
      assertFalse("exists(broken)", Files.exists(brokenLink))
      assertTrue(
        "NOFOLLOW - broken",
        Files.exists(brokenLink, LinkOption.NOFOLLOW_LINKS)
      )
      assertTrue("exists(correct)", Files.exists(correctLink))
      assertTrue(
        "NOFOLLOW - correct",
        Files.exists(correctLink, LinkOption.NOFOLLOW_LINKS)
      )
    }
  }

  @Test def filesCreateDirectoryCanCreateDirectory(): Unit = {
    val targetFile = File.createTempFile("test", ".tmp")
    val target = targetFile.toPath()
    assertTrue(targetFile.delete())

    Files.createDirectory(target)
    assertTrue("exists()", targetFile.exists())
    assertTrue("isDirectory()", targetFile.isDirectory())
  }

  @Test def filesCreateDirectoryThrowsIfTheFileAlreadyExists(): Unit = {
    val targetFile = File.createTempFile("test", ".tmp")
    val target = targetFile.toPath()

    assertThrows(
      classOf[FileAlreadyExistsException],
      Files.createDirectory(target)
    )
  }

  @Test def filesCreateDirectoriesCanCreateDirsIfNoneOfTheHierarchyExists()
      : Unit = {
    withTemporaryDirectory { dirFile =>
      val dir = dirFile.toPath()
      val p1 = dir.resolve("p1")
      val p2 = p1.resolve("p2")
      val p3 = p2.resolve("p3")

      assertFalse("!exists(p1)", Files.exists(p1))
      assertFalse("!exists(p2)", Files.exists(p2))
      assertFalse("!exists(p3)", Files.exists(p3))

      Files.createDirectories(p3)
      assertTrue("exists(p3)", Files.exists(p3))
    }
  }

  @Test def FilesCreateDirectoriesCanCreateDirsIfSomeOfTheHierarchyExists()
      : Unit = {
    withTemporaryDirectory { dirFile =>
      val dir = dirFile.toPath()
      val p1 = dir.resolve("p1")
      val p2 = p1.resolve("p2")
      val p3 = p2.resolve("p3")
      val p4 = p3.resolve("p4")

      assertFalse("a1", Files.exists(p1))
      assertFalse("a2", Files.exists(p2))
      assertFalse("a3", Files.exists(p3))
      assertFalse("a4", Files.exists(p4))

      Files.createDirectories(p2)

      assertTrue("a5", Files.exists(p1))
      assertTrue("a6", Files.exists(p2))
      assertFalse("a7", Files.exists(p3))
      assertFalse("a8", Files.exists(p4))

      Files.createDirectories(p4)
      assertTrue("a9", Files.exists(p1))
      assertTrue("a10", Files.exists(p2))
      assertTrue("a11", Files.exists(p3))
      assertTrue("a12", Files.exists(p4))
    }
  }

  @Test def filesCreateFileCanCreateNewFile(): Unit = {
    withTemporaryDirectory { dirFile =>
      val dir = dirFile.toPath()
      val file = dir.resolve("file")

      assertFalse("!exists()", Files.exists(file))
      Files.createFile(file)
      assertTrue("exists()", Files.exists(file))
    }
  }

  @Test def filesCreateFileThrowsIfTheFileAlreadyExists(): Unit = {
    withTemporaryDirectory { dirFile =>
      val dir = dirFile.toPath()
      val file = dir.resolve("file")

      assertFalse("!exists()", Files.exists(file))
      Files.createFile(file)
      assertTrue("exists()", Files.exists(file))

      assertThrows(classOf[FileAlreadyExistsException], Files.createFile(file))
    }
  }

  @Test def filesCreateLinkCanCreateLinks(): Unit = {
    withTemporaryDirectory { dirFile =>
      val dir = dirFile.toPath()
      val file = dir.resolve("file")
      val link = dir.resolve("link")

      Files.createFile(file)
      assertTrue("exists(file)", Files.exists(file))

      Files.createLink(link, file)
      assertTrue("exists(link)", Files.exists(link))
    }
  }

  @Test def filesCreateLinkThrowsIfTheFileAlreadyExists(): Unit = {
    withTemporaryDirectory { dirFile =>
      val dir = dirFile.toPath()
      val file = dir.resolve("file")
      val link = dir.resolve("link")

      Files.createFile(file)
      Files.createFile(link)
      assertTrue("exists(file)", Files.exists(file))
      assertTrue("exists(link)", Files.exists(link))

      assertThrows(
        classOf[FileAlreadyExistsException],
        Files.createLink(link, file)
      )
    }
  }

  @Test def filesCreateTempDirectoryWorksWithoutParentDirectory(): Unit = {
    val dir = Files.createTempDirectory("tmp")
    assertTrue(Files.exists(dir))
    assertTrue(Files.isDirectory(dir))

    val file = dir.resolve("file")
    assertFalse("!exists()", Files.exists(file))
    Files.createFile(file)
    assertTrue("exists()", Files.exists(file))
  }

  @Test def filesCreateTempDirectoryWorksWithParentDirectory(): Unit = {
    withTemporaryDirectory { parent =>
      val dir = Files.createTempDirectory(parent.toPath(), "tmp")
      assertTrue("a1", dir.getParent() == parent.toPath())
      assertTrue("a2", Files.exists(dir))
      assertTrue("a3", Files.isDirectory(dir))

      val file = dir.resolve("file")
      assertFalse("a4", Files.exists(file))
      Files.createFile(file)
      assertTrue("a5", Files.exists(file))
    }
  }

  /* If you live a Good Life, you will never have to parse this regex by hand.
   *
   * Java uses simple ASCII for numeric '\d', and alphanumeric '\w'.
   * 
   * The parse goes:
   *   - an optional single character 'a'
   *   - 1 to 19 digit characters
   *   - zero to 6 alphanumeric characters
   *   - one optional period, a.k.a full stop
   *   - zero or many alphanumeric characters.
   *
   * snake_case, kebab-case, and other symbols are not expected by this regex.
   * Those are left as an exercise for the person introducing the breaking
   * change.
   */

  // Triple quotes means no need to double backslash, easier to read.
  private val tempFileRegex = """^a?\d{1,19}\w{0,6}\.?\w*$""".r

  @Test def filesCreateTempDirectoryWorksWithNullPrefix(): Unit = {
    val dir = Files.createTempDirectory(null)
    try {
      assertTrue(
        "a1",
        tempFileRegex.findFirstIn(dir.getFileName.toString).isDefined
      )
      assertTrue("a2", Files.exists(dir))
      assertTrue("a3", Files.isDirectory(dir))
    } finally Files.delete(dir)
  }

  @Test def filesCreateTempDirectoryWorksWithShortPrefix(): Unit = {
    val dir = Files.createTempDirectory("a")
    try {
      assertTrue(
        "a1",
        tempFileRegex.findFirstIn(dir.getFileName.toString).isDefined
      )
      assertTrue("a2", Files.exists(dir))
      assertTrue("a3", Files.isDirectory(dir))
    } finally Files.delete(dir)
  }

  /* This test is @Ignore'd for Continuous Integration (CI) because
   * it creates, on purpose, an infinite loop when the condition described
   * in Issue 3738 is present: bad path argument causes an infinite loop.
   *
   * The test can be run manually. It passes on JVM and should soon pass
   * on Scala Native.
   *
   * If ever Scala Native gets the JUnit test timeout feature, this
   * test can be changed for use in CI.
   */

  // Issue 3738
  @Ignore
  @Test def filesCreateTempDirectoryDoesNotLoopInfinitely(): Unit = {
    val path = Paths.get("/+")

    try {
      val f = Files.createTempFile(path, "Alpha", "Omega")
      Files.delete(f)
      fail("expected IOException or a sub-class of it")
    } catch {
      case e: IOException =>
    }
  }

  // Issue 4381, part 1
  @Test def filesCreateTempDirectoryDefaultsToJvmPosixPermissions(): Unit = {
    assumeFalse("Not testing Posix permissions on Windows", isWindows)

    withTemporaryDirectoryPath { dirPath =>
      val tmpDirName = "expectPosixPermissions_OwnerRWX_GroupOtherNone_"
      val tmpDirPath = Files.createTempDirectory(dirPath, tmpDirName)

      val tmpDirFile = tmpDirPath.toFile()

      assertTrue(
        "empty directory",
        tmpDirFile.exists() &&
          tmpDirFile.isDirectory() &&
          tmpDirFile.list().isEmpty
      )

      // JVM default, by implemention, not specification
      val expectedPermissions = "rwx------"

      val actualPermissions =
        PosixFilePermissions.toString(Files.getPosixFilePermissions(tmpDirPath))

      assertEquals(
        "POSIX permissions",
        expectedPermissions,
        actualPermissions
      )

      cleanupWorkArea(dirPath, tmpDirPath)
    }
  }

  // Issue 4381, part 2
  @Test def filesCreateTempDirectoryUsesSpecifiedPosixPermissions(): Unit = {
    assumeFalse("Not testing Posix permissions on Windows", isWindows)

    withTemporaryDirectoryPath { dirPath =>
      val tmpDirName = "specifiedPosixPermissions_OwnerRX_GroupOtherNone_"

      /* The usual umask values of 022 and 077 will mask off Group and Other
       * bits, so change one of the User bits.
       * Still, there are some rare umask value which can break this test,
       * say 122.
       */
      val expectedPermissions = "r-x------"

      val createWithPermissionsAttr =
        PosixFilePermissions.asFileAttribute(
          PosixFilePermissions.fromString(expectedPermissions)
        )

      val tmpDirPath =
        Files.createTempDirectory(
          dirPath,
          tmpDirName,
          createWithPermissionsAttr
        )

      val tmpDirFile = tmpDirPath.toFile()

      assertTrue(
        "empty directory",
        tmpDirFile.exists() &&
          tmpDirFile.isDirectory() &&
          tmpDirFile.list().isEmpty
      )

      val actualPermissions =
        PosixFilePermissions.toString(Files.getPosixFilePermissions(tmpDirPath))

      assertEquals(
        "POSIX permissions",
        expectedPermissions,
        actualPermissions
      )

      cleanupWorkArea(dirPath, tmpDirPath)
    }
  }

  @Test def filesCreateTempFileWorksWithNullPrefix(): Unit = {
    val file = Files.createTempFile(null, "txt")
    try {
      assertTrue(
        "a1",
        tempFileRegex.findFirstIn(file.getFileName.toString).isDefined
      )
      assertTrue("a2", Files.exists(file))
      assertTrue("a3", Files.isRegularFile(file))
    } finally Files.delete(file)
  }

  @Test def filesCreateTempFileWorksWithShortPrefix(): Unit = {
    val file = Files.createTempFile("a", null)
    try {
      assertTrue(
        "a1",
        tempFileRegex.findFirstIn(file.getFileName.toString).isDefined
      )
      assertTrue("a2", Files.exists(file))
      assertTrue("a3", Files.isRegularFile(file))
    } finally Files.delete(file)
  }

  /* See comments above filesCreateTempDirectoryDoesNotLoopInfinitely()
   * test above. Same song, next verse.
   */

  // Issue 3738
  @Ignore
  @Test def filesCreateTempFileDoesNotLoopInfinitely(): Unit = {
    val path = Paths.get("/+")

    try {
      val f = Files.createTempFile(path, "Alpha", "Omega")
      Files.delete(f)
      fail("expected IOException or a sub-class of it")
    } catch {
      case e: IOException =>
    }
  }

  @Test def filesIsRegularFileReportsFilesAsSuch(): Unit = {
    withTemporaryDirectory { dirFile =>
      val dir = dirFile.toPath
      val file = dir.resolve("file")
      Files.createFile(file)
      assertTrue("a1", Files.exists(file))
      assertTrue("a2", Files.isRegularFile(file))
    }
  }

  @Test def filesIsRegularFileHandlesDirectories(): Unit = {
    withTemporaryDirectory { dirFile =>
      val dir = dirFile.toPath
      assertTrue("a1", Files.exists(dir))
      assertFalse("a2", Files.isRegularFile(dir))
    }
  }

  @Test def filesIsRegularFileHandlesSymlinks(): Unit = {
    assumeShouldTestSymlinks()

    withTemporaryDirectory { dirFile =>
      val dir = dirFile.toPath
      val file = dir.resolve("file")
      val link = dir.resolve("link")
      Files.createFile(file)
      Files.createSymbolicLink(link, file)
      assertTrue("a1", Files.exists(file))
      assertTrue("a2", Files.isRegularFile(file))
      assertTrue("a3", Files.isSymbolicLink(link))
      assertTrue("a4", Files.isRegularFile(link))
      assertFalse("a5", Files.isRegularFile(link, LinkOption.NOFOLLOW_LINKS))
    }
  }

  @Test def filesCreateTempFileWorksWithoutParentDirectory(): Unit = {
    val tmp = Files.createTempFile("temp", ".tmp")
    assertTrue("a1", Files.exists(tmp))
    assertTrue("a2", Files.isRegularFile(tmp))
  }

  @Test def filesCreateTempFileWorksWithParentDirectory(): Unit = {
    withTemporaryDirectory { parent =>
      val tmp = Files.createTempFile(parent.toPath(), "temp", ".tmp")
      assertTrue("a1", tmp.getParent() == parent.toPath())
      assertTrue("a2", Files.exists(tmp))
      assertTrue("a3", Files.isRegularFile(tmp))
    }
  }

  @Test def filesDeleteCanDeleteFiles(): Unit = {
    withTemporaryDirectory { dirFile =>
      val dir = dirFile.toPath()
      val file = dir.resolve("file")
      Files.createFile(file)

      assertTrue("a1", Files.exists(file))
      assertTrue("a2", Files.isRegularFile(file))
      Files.delete(file)
      assertFalse("a3", Files.exists(file))
    }
  }

  @Test def filesDeleteCanDeleteEmptyDirectories(): Unit = {
    withTemporaryDirectory { dirFile =>
      val dir = dirFile.toPath()
      val subdir = dir.resolve("subdir")
      Files.createDirectory(subdir)
      assertTrue("a1", Files.exists(subdir))
      assertTrue("a2", Files.isDirectory(subdir))
      Files.delete(subdir)
      assertFalse("a3", Files.exists(subdir))
    }
  }

  // I3878
  @Test def filesDeleteDoesNotFollowUnbrokenSymlinks(): Unit = {
    assumeShouldTestSymlinks()

    withTemporaryDirectory { dirFile =>
      val dir = dirFile.toPath()
      val unbrokenLink = dir.resolve("unbroken-link")
      val linkTarget = dir.resolve("link-target")

      Files.createFile(linkTarget)
      assertTrue("link target should exist", Files.exists(linkTarget))

      Files.createSymbolicLink(unbrokenLink, linkTarget)
      Files.delete(unbrokenLink)

      // deleted symlink but not its target
      assertFalse("symlink was not deleted", Files.exists(unbrokenLink))
      assertTrue("target was deleted", Files.exists(linkTarget))
    }
  }

  // I3878
  @Test def filesDeleteDoesNotFollowBrokenSymlinks(): Unit = {
    assumeShouldTestSymlinks()

    withTemporaryDirectory { dirFile =>
      val dir = dirFile.toPath()
      val brokenLink = dir.resolve("broken-link")
      Files.createSymbolicLink(brokenLink, dir.resolve("doesnt-exist"))
      Files.delete(brokenLink)
    }
  }

  @Test def filesDeleteThrowsWhenDeletingNonExistingFile(): Unit = {
    withTemporaryDirectory { dirFile =>
      val dir = dirFile.toPath()
      val nonexisting = dir.resolve("nonexisting")
      assertFalse("a1", Files.exists(nonexisting))

      assertThrows(classOf[NoSuchFileException], Files.delete(nonexisting))
    }
  }

  @Test def filesDeleteThrowsWhenDeletingNonEmptyDirectory(): Unit = {
    withTemporaryDirectory { dirFile =>
      val dir = dirFile.toPath()
      val subdir = dir.resolve("subdir")
      val file = subdir.resolve("file")
      Files.createDirectory(subdir)
      Files.createFile(file)
      assertTrue("a1", Files.exists(subdir))
      assertTrue("a2", Files.isDirectory(subdir))

      assertThrows(classOf[DirectoryNotEmptyException], Files.delete(subdir))
    }
  }

  @Test def filesDeleteIfExistsWorksIfTheFileExists(): Unit = {
    withTemporaryDirectory { dirFile =>
      val dir = dirFile.toPath()
      val file = dir.resolve("file")
      Files.createFile(file)
      assertTrue("a1", Files.exists(file))
      assertTrue("a2", Files.isRegularFile(file))
      assertTrue("a3", Files.deleteIfExists(file))
      assertFalse("a4", Files.exists(file))
    }
  }

  @Test def filesDeleteIfExistsWorksIfTheFileDoesNotExist(): Unit = {
    withTemporaryDirectory { dirFile =>
      val dir = dirFile.toPath()
      val file = dir.resolve("file")
      assertFalse("a1", Files.exists(file))
      assertFalse("a2", Files.deleteIfExists(file))
      assertFalse("a3", Files.exists(file))
    }
  }

  @Test def filesListListsFiles(): Unit = {
    withTemporaryDirectory { dirFile =>
      val dir = dirFile.toPath()
      val f0 = dir.resolve("f0")
      val f1 = dir.resolve("f1")
      val d0 = dir.resolve("d0")
      val f2 = d0.resolve("f2")

      Files.createDirectory(d0)
      Files.createFile(f0)
      Files.createFile(f1)
      Files.createFile(f2)
      assertTrue("a1", Files.exists(d0) && Files.isDirectory(d0))
      assertTrue("a2", Files.exists(f0) && Files.isRegularFile(f0))
      assertTrue("a3", Files.exists(f1) && Files.isRegularFile(f1))
      assertTrue("a4", Files.exists(f2) && Files.isRegularFile(f2))

      val fileStream = Files.list(dir)
      try {
        val it = fileStream.iterator()

        val files = new java.util.HashSet[Path]()
        while (it.hasNext())
          files.add(it.next())

        assertEquals("file count", 3, files.size)

        assertTrue(
          s"entry not found in stream: '${d0.toString()}'",
          files.contains(d0)
        )
        assertTrue(
          s"entry not found in stream: '${f0.toString()}'",
          files.contains(f0)
        )
        assertTrue(
          s"entry not found in stream '{f1.toString()}'",
          files.contains(f1)
        )
      } finally fileStream.close()
    }
  }

  @Test def filesReadSymbolicLinkCanReadValidSymbolicLink(): Unit = {
    assumeShouldTestSymlinks()

    withTemporaryDirectory { dirFile =>
      val dir = dirFile.toPath()
      val link = dir.resolve("link")
      val file = dir.resolve("file")
      Files.createFile(file)
      Files.createSymbolicLink(link, file)

      assertTrue("a1", Files.exists(file))
      assertTrue("a2", Files.exists(link))
      assertEquals("a3", file, Files.readSymbolicLink(link))
    }
  }

  @Test def filesReadSymbolicLinkCanReadBrokenSymbolicLink(): Unit = {
    // Does fail on Windows. Cannot open broken link
    assumeShouldTestSymlinks()

    withTemporaryDirectory { dirFile =>
      val dir = dirFile.toPath()
      val brokenLink = dir.resolve("link")
      val file = dir.resolve("file")
      Files.createSymbolicLink(brokenLink, file)

      assertFalse("a1", Files.exists(file))
      assertTrue("a2", Files.exists(brokenLink, LinkOption.NOFOLLOW_LINKS))
      assertEquals("a3", file, Files.readSymbolicLink(brokenLink))
    }
  }

  @Test def filesReadSymbolicLinkCanReadRecursive(): Unit = {
    assumeShouldTestSymlinks()

    withTemporaryDirectory { dirFile =>
      val dir = dirFile.toPath()
      val file = Files.createFile(dir.resolve("file"))
      val link1 = Files.createSymbolicLink(dir.resolve("link1"), file)
      val link2 = Files.createSymbolicLink(dir.resolve("link2"), link1)
      val link3 =
        Files.createSymbolicLink(dir.resolve("link3"), Paths.get("./link2"))

      assertTrue("a1", Files.exists(file))
      assertTrue("a2", Files.exists(link1))
      assertTrue("a2", Files.exists(link2))
      assertEquals("read 1", file, Files.readSymbolicLink(link1))
      assertEquals("read 2", link1, Files.readSymbolicLink(link2))
      assertEquals(
        "read 3",
        Paths.get(".", "link2"),
        Files.readSymbolicLink(link3)
      )
    }
  }

  @Test def filesWalk_File(): Unit = {
    withTemporaryDirectory { dirFile =>
      val f0 = dirFile.toPath.resolve("f0")

      Files.createFile(f0)
      assertTrue("a1", Files.exists(f0) && Files.isRegularFile(f0))

      val it = Files.walk(f0).iterator() // walk file, not directory

      val files = scala.collection.mutable.Set.empty[Path]
      while (it.hasNext) {
        files += it.next()
      }

      assertEquals("Unexpected number of files", 1, files.size)
      assertTrue("stream should contain starting file", files contains f0)
    }
  }

  @Test def filesWalk_EmptyDir(): Unit = {
    withTemporaryDirectory { dirFile =>
      val dir = dirFile.toPath()
      val it = Files.walk(dir).iterator()
      val files = scala.collection.mutable.Set.empty[Path]
      while (it.hasNext) {
        files += it.next()
      }

      assertEquals("Unexpected number of files", 1, files.size)
      assertTrue("stream should contain starting dir", files contains dir)
    }
  }

  @Test def filesWalk_Directory_OneDeep(): Unit = {
    withTemporaryDirectory { dirFile =>
      val dir = dirFile.toPath()
      val f1 = dir.resolve("f1")
      val f2 = dir.resolve("f2")
      val d1 = dir.resolve("d1")
      val d1f1 = d1.resolve("d1f1")

      Files.createFile(f1)
      Files.createFile(f2)
      assertTrue("a1", Files.exists(f1) && Files.isRegularFile(f1))
      assertTrue("a2", Files.exists(f2) && Files.isRegularFile(f2))

      Files.createDirectory(d1)
      Files.createFile(d1f1)
      assertTrue("a3", Files.exists(d1) && Files.isDirectory(d1))
      assertTrue("a4", Files.exists(d1f1) && Files.isRegularFile(d1f1))

      val it = Files.walk(dir).iterator()
      val files = scala.collection.mutable.Set.empty[Path]
      while (it.hasNext()) {
        files += it.next()
      }

      assertEquals("Unexpected number of files", 5, files.size)

      assertTrue("stream should contain starting dir", files contains dir)
      assertTrue("a5", files contains f1)
      assertTrue("a6", files contains f1)
      assertTrue("a7", files contains d1)
      assertTrue("a8", files contains d1f1)
    }
  }

  @Test def filesWalk_Directory_TwoDeep(): Unit = {
    withTemporaryDirectory { dirFile =>
      val dir = dirFile.toPath()

      val f1 = dir.resolve("f1")
      val f2 = dir.resolve("f2")

      val d1 = dir.resolve("d1")
      val d1f1 = d1.resolve("d1f1")

      val d2 = d1.resolve("d2")
      val d2f1 = d2.resolve("d2f1")

      Files.createFile(f1)
      Files.createFile(f2)
      assertTrue("a1", Files.exists(f1) && Files.isRegularFile(f1))
      assertTrue("a2", Files.exists(f2) && Files.isRegularFile(f2))

      Files.createDirectory(d1)
      Files.createFile(d1f1)
      assertTrue("a3", Files.exists(d1) && Files.isDirectory(d1))
      assertTrue("a4", Files.exists(d1f1) && Files.isRegularFile(d1f1))

      Files.createDirectory(d2)
      Files.createFile(d2f1)
      assertTrue("a5", Files.exists(d2) && Files.isDirectory(d2))
      assertTrue("a6", Files.exists(d2f1) && Files.isRegularFile(d2f1))

      val it = Files.walk(dir).iterator()
      val files = scala.collection.mutable.Set.empty[Path]
      while (it.hasNext()) {
        files += it.next()
      }

      assertEquals("Unexpected number of files", 7, files.size)

      assertTrue("stream should contain starting dir", files contains dir)

      assertTrue("a7", files contains f1)
      assertTrue("a8", files contains f2)

      assertTrue("a9", files contains d1)
      assertTrue("a10", files contains d1f1)

      assertTrue("a11", files contains d2)
      assertTrue("a12", files contains d2f1)
    }
  }

  @Test def filesWalkFollowsSymlinks(): Unit = {
    assumeShouldTestSymlinks()

    withTemporaryDirectory { dirFile =>
      val dir = dirFile.toPath()

      val d0 = dir.resolve("d0")
      val f0 = d0.resolve("f0")
      val f1 = d0.resolve("f1")
      val link = d0.resolve("link")

      val d1 = dir.resolve("d1")
      val f2 = d1.resolve("f2")

      Files.createDirectory(d0)
      Files.createFile(f0)
      Files.createFile(f1)
      Files.createSymbolicLink(link, d1)

      Files.createDirectory(d1)
      Files.createFile(f2)

      assertTrue("a1", Files.exists(d0) && Files.isDirectory(d0))
      assertTrue("a2", Files.exists(f0) && Files.isRegularFile(f0))
      assertTrue("a3", Files.exists(f1) && Files.isRegularFile(f1))
      assertTrue("a4", Files.exists(f2) && Files.isRegularFile(f2))

      val it = Files.walk(d0, FileVisitOption.FOLLOW_LINKS).iterator()
      val files = scala.collection.mutable.Set.empty[Path]
      while (it.hasNext()) {
        files += it.next()
      }
      assertEquals("files.size()", 5, files.size)
      assertTrue("a5", files contains d0)
      assertTrue("a6", files contains f0)
      assertTrue("a7", files contains f1)
      assertTrue("a8", files contains link)
    }
  }

  @Test def filesWalkCorrectSymlinksAttributes(): Unit = {
    assumeShouldTestSymlinks()

    withTemporaryDirectory { dirFile =>
      val dir = dirFile.toPath()
      val dir2 = Files.createDirectories(dir.resolve("dir2"))
      val file = Files.createFile(dir.resolve("file"))
      val link1 = Files.createSymbolicLink(dir.resolve("link1"), file)
      val link2 = Files.createSymbolicLink(dir.resolve("link2"), link1)
      val link3 =
        Files.copy(link2, dir.resolve("link3"), LinkOption.NOFOLLOW_LINKS)
      val link4 =
        Files.createSymbolicLink(dir.resolve("link4"), Paths.get(".", "link2"))
      val link5 =
        Files.createSymbolicLink(
          dir2.resolve("link5"),
          Paths.get("..", "link2")
        )
      val link6 = Files.createSymbolicLink(dir.resolve("link6"), link5)

      val links = Seq(link1, link2, link3, link4, link5, link6)
      val linksAbs = links.map(_.toAbsolutePath())
      (Seq(file) ++ links).foreach { path =>
        assertTrue(s"exists $path", Files.exists(path))
      }
      def check(followLinks: Boolean): Unit = {
        Files.walkFileTree(
          dir,
          if (followLinks) EnumSet.of(FileVisitOption.FOLLOW_LINKS)
          else Collections.emptySet(),
          Int.MaxValue,
          new FileVisitor[Path] {
            override def postVisitDirectory(
                dir: Path,
                error: IOException
            ): FileVisitResult = FileVisitResult.CONTINUE
            override def preVisitDirectory(
                dir: Path,
                attributes: BasicFileAttributes
            ): FileVisitResult = FileVisitResult.CONTINUE

            override def visitFile(
                file: Path,
                attributes: BasicFileAttributes
            ): FileVisitResult = {
              if (linksAbs.contains(file.toAbsolutePath())) {
                assertEquals(
                  s"isRegularFile $file followLinks=$followLinks",
                  followLinks,
                  attributes.isRegularFile()
                )
                assertEquals(
                  s"isSymLink $file followLinks=$followLinks",
                  !followLinks,
                  attributes.isSymbolicLink()
                )
              }
              FileVisitResult.CONTINUE
            }
            override def visitFileFailed(
                file: Path,
                error: IOException
            ): FileVisitResult = FileVisitResult.CONTINUE
          }
        )
      }
      check(followLinks = false)
      check(followLinks = true)

    }
  }

  @Test def filesWalkDetectsCycles(): Unit = {
    assumeShouldTestSymlinks()

    withTemporaryDirectory { dirFile =>
      val dir = dirFile.toPath()

      val d0 = dir.resolve("d0")
      val d1 = d0.resolve("d1")
      val link = d1.resolve("link")

      Files.createDirectory(d0)
      Files.createDirectory(d1)
      Files.createSymbolicLink(link, d0)

      val it = Files.walk(d0, FileVisitOption.FOLLOW_LINKS).iterator()
      val expected = Set(d0, d1)
      assertTrue("a1", expected contains it.next())
      assertTrue("a2", expected contains it.next())
      val thrown = Try { it.next() }
      assertTrue(thrown.isInstanceOf[Failure[Path]])
      val exception = thrown.asInstanceOf[Failure[Path]].exception
      assertTrue(exception.isInstanceOf[UncheckedIOException])
      val cause = exception.asInstanceOf[UncheckedIOException].getCause()
      assertTrue(cause.isInstanceOf[IOException])
    }
  }

  @Test def filesWalkMaxDepthNegative(): Unit = {
    withTemporaryDirectory { dirFile =>
      val dir = dirFile.toPath()
      val d1 = dir.resolve("test-project") // no need to actually create
      val maxDepth = -1

      try {
        assertThrows(
          classOf[IllegalArgumentException],
          Files.walk(d1, -1)
        )
      } finally {}
    }
  }

  @Test def filesWalkMaxDepthZero(): Unit = {
    withTemporaryDirectory { dirFile =>
      val dir = dirFile.toPath()
      val d1 = dir.resolve("test-project")

      Files.createDirectory(d1)

      val result = Files.walk(d1, 0).toArray()

      val expectedLength = 1
      assertEquals("length", expectedLength, result.length)

      val expectedPath = d1
      assertEquals("content", expectedPath, result(0).asInstanceOf[Path])

      // Delete files only on succcess, otherwise leave detritus for study.
      Files.delete(d1)
      Files.delete(dir)
    }
  }

  // Issue 3471, 1 of 2 broken methods
  @Test def filesWalkMaxDepthPositive(): Unit = {
    withTemporaryDirectory { dirFile =>
      val dir = dirFile.toPath()
      val d1 = dir.resolve("test-project")
      val d1f1 = d1.resolve("default.properties")

      val d2 = d1.resolve("src")
      val d2f1 = d2.resolve("project.scala")
      val d2f2 = d2.resolve("test.scala")

      Files.createDirectory(d1)
      Files.createFile(d1f1)

      Files.createDirectory(d2)
      Files.createFile(d2f1)
      Files.createFile(d2f2)

      assertTrue("a1", Files.exists(d1) && Files.isDirectory(d1))
      assertTrue("a2", Files.exists(d1f1) && Files.isRegularFile(d1f1))

      assertTrue("a3", Files.exists(d2) && Files.isDirectory(d2))
      assertTrue("a4", Files.exists(d2f1) && Files.isRegularFile(d2f1))
      assertTrue("a5", Files.exists(d2f2) && Files.isRegularFile(d2f2))

      val paths = Files
        .walk(d1, 1)
        .toArray(
          new IntFunction[Array[Path]]() {
            def apply(value: Int): Array[Path] = new Array[Path](value)
          }
        )

      val expectedLength = 3
      assertEquals("length", expectedLength, paths.length)

      // Expect the starting directory to always be the first (0-th) element.
      assertEquals("start", d1, paths(0))

      /* The order of the rest of the elements in which are visited is
       * not defined. Sort to ease testing. We know the expected names
       * so we know and can test the expected sorted order.
       */

      Arrays.sort(paths, java.util.Comparator.naturalOrder[Path]())

      assertEquals("content_1", d1f1, paths(1))
      assertEquals("content_2", d2, paths(2))

      // Delete files only on succcess, otherwise leave detritus for study.
      Files.delete(d2f2) // Delete in opposite order of creation
      Files.delete(d2f1)
      Files.delete(d2)

      Files.delete(d1f1)
      Files.delete(d1)

      Files.delete(dir)
    }
  }

  @Test def filesWalkFileTreeMaxDepthNegative(): Unit = {
    withTemporaryDirectory { dirFile =>
      val dir = dirFile.toPath()
      val d1 = dir.resolve("test-project") // no need to actually create

      val visitor = new QueueingVisitor
      val fvoSet = EnumSet.noneOf(classOf[FileVisitOption])

      try {
        assertThrows(
          classOf[IllegalArgumentException],
          Files.walkFileTree(d1, fvoSet, -1, visitor)
        )
      } finally {
        Files.delete(dir)
      }
    }
  }

  @Test def filesWalkFileTreeMaxDepthZero(): Unit = {
    withTemporaryDirectory { dirFile =>
      val dir = dirFile.toPath()
      val d1 = dir.resolve("test-project")

      Files.createDirectory(d1)

      val visitor = new WftQueueingVisitor

      // do not FOLLOW_LINKS, see Test filesWalkFileTreeMaxDepthPositive
      val fvoSet = EnumSet.noneOf(classOf[FileVisitOption])

      Files.walkFileTree(d1, fvoSet, 0, visitor)

      val expected = 1
      assertEquals("a1", expected, visitor.length())

      assertEquals("visitor", d1, visitor.dequeue())

      // Delete files only on succcess, otherwise leave detritus for study.
      Files.delete(d1)
      Files.delete(dir)
    }
  }

  // Issue 3471, 2 of 2 broken methods.
  @Test def filesWalkFileTreeMaxDepthPositive(): Unit = {
    withTemporaryDirectory { dirFile =>
      val dir = dirFile.toPath()
      val d1 = dir.resolve("test-project")
      val d1f1 = d1.resolve("default.properties")

      val d2 = d1.resolve("src")
      val d2f1 = d2.resolve("project.scala")
      val d2f2 = d2.resolve("test.scala")

      Files.createDirectory(d1)
      Files.createFile(d1f1)

      Files.createDirectory(d2)
      Files.createFile(d2f1)
      Files.createFile(d2f2)

      assertTrue("a1", Files.exists(d1) && Files.isDirectory(d1))
      assertTrue("a2", Files.exists(d1f1) && Files.isRegularFile(d1f1))

      assertTrue("a3", Files.exists(d2) && Files.isDirectory(d2))
      assertTrue("a4", Files.exists(d2f1) && Files.isRegularFile(d2f1))
      assertTrue("a5", Files.exists(d2f2) && Files.isRegularFile(d2f2))

      // Before tracing magic here, read long comment at top of the class.
      val visitor = new OsLibLikeQueueingVisitor

      /* Java doc for Files.walkFileTree(start, visitor) describes
       * using EnumSet.noneOf(FileVisitOption.class). Use that idiom.
       *
       * Do not FOLLOW_LINKS, os-lib walk(), as used in Issue, does not.
       */
      val fvoSet = EnumSet.noneOf(classOf[FileVisitOption])

      Files.walkFileTree(d1, fvoSet, 1, visitor)

      val expected = 2 // JVM value

      assertEquals("visitor length", expected, visitor.length())

      /* The Path names within any given may be returned in arbitrary
       * order. Waste some CPU cycles to sort them in order to avoid
       * complicated & likely fragile "if" logic here. There are only
       * two files and we know/have_chosen the order in which they will
       * sort. Lazy, but done.
       */

      val sortedPaths = new TreeSet[Path]()
      sortedPaths.add(visitor.dequeue())
      sortedPaths.add(visitor.dequeue())

      assertEquals("first path", d1f1, sortedPaths.first())
      assertEquals("second path", d2, sortedPaths.last())

      // Delete files only on succcess, otherwise leave detritus for study.
      Files.delete(d2f2) // Delete in opposite order of creation
      Files.delete(d2f1)
      Files.delete(d2)

      Files.delete(d1f1)
      Files.delete(d1)

      Files.delete(dir)
    }
  }

  @Test def filesWalkFileTreeCanBeTerminated(): Unit = {
    withTemporaryDirectory { dirFile =>
      val dir = dirFile.toPath()
      val f0 = dir.resolve("f0")
      val f1 = dir.resolve("f1")
      val d0 = dir.resolve("d0")
      val f2 = d0.resolve("f2")

      Files.createDirectory(d0)
      Files.createFile(f0)
      Files.createFile(f1)
      Files.createFile(f2)
      assertTrue("a1", Files.exists(d0) && Files.isDirectory(d0))
      assertTrue("a2", Files.exists(f0) && Files.isRegularFile(f0))
      assertTrue("a3", Files.exists(f1) && Files.isRegularFile(f1))
      assertTrue("a4", Files.exists(f2) && Files.isRegularFile(f2))

      val visitor = new QueueingVisitor()
      val terminatingVisitor = new QueueingVisitor {
        override def visitFile(
            file: Path,
            attributes: BasicFileAttributes
        ): FileVisitResult =
          if (file == f2) FileVisitResult.TERMINATE
          else super.visitFile(file, attributes)
      }
      Files.walkFileTree(dir, visitor)
      Files.walkFileTree(dir, terminatingVisitor)

      val expected = scala.collection.mutable.Set.empty[Path]
      var continue = true
      while (continue) {
        val p = visitor.dequeue()
        if (p == f2) continue = false
        else expected += p
      }
      val found = scala.collection.mutable.Set.empty[Path]
      while (!terminatingVisitor.isEmpty()) {
        found += terminatingVisitor.dequeue()
      }
      assertEquals("a5", expected, found)
    }
  }

  @Test def filesWalkFileTreeCanSkipSubtrees(): Unit = {
    withTemporaryDirectory { dirFile =>
      val dir = dirFile.toPath()
      val f0 = dir.resolve("f0")
      val f1 = dir.resolve("f1")
      val d0 = dir.resolve("d0")
      val f2 = d0.resolve("f2")

      Files.createDirectory(d0)
      Files.createFile(f0)
      Files.createFile(f1)
      Files.createFile(f2)
      assertTrue("a1", Files.exists(d0) && Files.isDirectory(d0))
      assertTrue("a2", Files.exists(f0) && Files.isRegularFile(f0))
      assertTrue("a3", Files.exists(f1) && Files.isRegularFile(f1))
      assertTrue("a4", Files.exists(f2) && Files.isRegularFile(f2))

      val visitor = new QueueingVisitor {
        override def preVisitDirectory(
            dir: Path,
            attributes: BasicFileAttributes
        ): FileVisitResult =
          if (dir == d0) FileVisitResult.SKIP_SUBTREE
          else super.preVisitDirectory(dir, attributes)
      }
      Files.walkFileTree(dir, visitor)
      val expected = Map(dir -> 2, f0 -> 1, f1 -> 1)
      val result = scala.collection.mutable.Map.empty[Path, Int]
      while (!visitor.isEmpty()) {
        val f = visitor.dequeue()
        val count = result.getOrElse(f, 0)
        result(f) = count + 1
      }
      assertEquals("a5", expected, result)
    }
  }

  @Test def filesWalkFileTreeCanSkipSiblings(): Unit = {
    assumeNotJVMCompliant()

    withTemporaryDirectory { dirFile =>
      val dir = dirFile.toPath()
      val f1 = dir.resolve("f1")
      val f2 = dir.resolve("f2")
      val f3 = dir.resolve("f3")
      val f4 = dir.resolve("f4")
      val d1 = dir.resolve("d1")
      val d1f1 = d1.resolve("d1f1")
      val d1f2 = d1.resolve("d1f2")

      Files.createFile(f1)
      Files.createFile(f2)
      Files.createFile(f3)
      Files.createFile(f4)

      Files.createDirectory(d1)
      Files.createFile(d1f1)
      Files.createFile(d1f2)

      assertTrue("a1", Files.exists(f1) && Files.isRegularFile(f1))
      assertTrue("a2", Files.exists(f2) && Files.isRegularFile(f2))
      assertTrue("a3", Files.exists(f3) && Files.isRegularFile(f3))
      assertTrue("a4", Files.exists(f4) && Files.isRegularFile(f4))

      assertTrue("a5", Files.exists(d1) && Files.isDirectory(d1))

      assertTrue("a6", Files.exists(d1f1) && Files.isRegularFile(d1f1))
      assertTrue("a7", Files.exists(d1f2) && Files.isRegularFile(d1f2))

      /* Why skip after f2? f2 is not the first file created and
       * it is not the last. It also _could_ have plenty of siblings,
       * including a directory to mix things up.
       *
       * Besides, it spoke to me.
       */

      val skippingVisitor = new WftQueueingVisitor {
        override def visitFile(
            path: Path,
            attributes: BasicFileAttributes
        ): FileVisitResult = {
          super.visitFile(path, attributes)
          if (path == f2) FileVisitResult.SKIP_SIBLINGS
          else FileVisitResult.CONTINUE
        }
      }

      Files.walkFileTree(dir, skippingVisitor)

      /* The operating system can return the elements of a given directory
       * in any order. f2 should be the last element visited, no matter
       * the order returned from the os.
       */

      skippingVisitor.lastOption() match {
        case None    => fail("Unexpected empty visited list")
        case Some(v) =>
          assertEquals(s"path '${v}' was not skipped", f2, v)
      }
    }
  }

  // The next two tests were inspired by Issue #1354
  // "NIO File Walker fails on broken links."

  @Test def filesWalkFileTreeRespectsFileVisitOptionFollowLinksNo(): Unit = {
    assumeShouldTestSymlinks()

    withTemporaryDirectoryPath { dirPath =>
      val context = new FollowLinksTestsContext(dirPath)

      val visitor = new QueueingVisitor()

      // Will not follow links, so broken link does not throw exception.
      Files.walkFileTree(dirPath, visitor)

      val resultSet = visitorToFileNamesSet(visitor)
      val resultLength = resultSet.size

      val expectedFileNamesSet = context.expectedFollowFilesSet()
      val expectedLength = expectedFileNamesSet.size

      assertEquals(s"result length:", expectedLength, resultLength)
      assertEquals(s"result set:", expectedFileNamesSet, resultSet)
    }
  }

  @Test def filesWalkFileTreeRespectsFileVisitOptionFollowLinksYes(): Unit = {
    assumeShouldTestSymlinks()

    withTemporaryDirectoryPath { dirPath =>
      val context = new FollowLinksTestsContext(dirPath)

      val visitor = new QueueingVisitor()

      // Follow the broken link; expect an exception will not be thrown.
      val fvoSet = Set(FileVisitOption.FOLLOW_LINKS).toJavaSet
      Files.walkFileTree(dirPath, fvoSet, Int.MaxValue, visitor)
    }
  }

  // Issue 3744
  @Test def filesWalkFileTreeDetectsCycles(): Unit = {
    assumeShouldTestSymlinks()

    withTemporaryDirectoryPath { dirPath =>

      val ancestorDir = dirPath.resolve("ancestor")

      Files.createDirectories(ancestorDir)
      assertTrue("ancestor directory exists()", Files.exists(ancestorDir))

      val linkToAncestor = ancestorDir.resolve("linkToAncestor")
      Files.createSymbolicLink(linkToAncestor, ancestorDir)
      assertTrue("link exists()", Files.exists(linkToAncestor))

      val limit = 2
      var count = 0

      val linkLoopBreakingVisitor =
        new SimpleFileVisitor[Path] {
          override def visitFile(f: Path, attrs: BasicFileAttributes) = {
            count += 1
            if (count < limit)
              FileVisitResult.CONTINUE
            else
              FileVisitResult.TERMINATE
          }

          override def preVisitDirectory(
              dir: Path,
              attributes: BasicFileAttributes
          ): FileVisitResult = {
            FileVisitResult.CONTINUE
          }
        }

      val fvoSet = Set(FileVisitOption.FOLLOW_LINKS).toJavaSet

      assertThrows(
        classOf[FileSystemLoopException],
        Files.walkFileTree(
          dirPath,
          fvoSet,
          Int.MaxValue,
          linkLoopBreakingVisitor
        )
      )

      // JVM will throw FileSystemLoopException before count ever gets += 1.
      assertEquals("Expected FileSystemLoopException", 0, count)
    }
  }

  @Test def filesFindFindsFiles(): Unit = {
    withTemporaryDirectory { dirFile =>
      val dir = dirFile.toPath()
      val f0 = dir.resolve("f0")
      val f1 = dir.resolve("f1")
      val d0 = dir.resolve("d0")
      val f2 = d0.resolve("f2")

      Files.createDirectory(d0)
      Files.createFile(f0)
      Files.createFile(f1)
      Files.createFile(f2)
      assertTrue("a1", Files.exists(d0) && Files.isDirectory(d0))
      assertTrue("a2", Files.exists(f0) && Files.isRegularFile(f0))
      assertTrue("a3", Files.exists(f1) && Files.isRegularFile(f1))
      assertTrue("a4", Files.exists(f2) && Files.isRegularFile(f2))

      val predicate = new BiPredicate[Path, BasicFileAttributes] {
        override def test(path: Path, attrs: BasicFileAttributes): Boolean =
          path.getFileName.toString.startsWith("f")
      }
      val it = Files.find(dir, 10, predicate).iterator
      val expected = Set(f0, f1, f2)

      assertTrue("a5", expected contains it.next())
      assertTrue("a6", expected contains it.next())
      assertTrue("a7", expected contains it.next())
    }
  }

  private def setupFindSymlinkContext(
      top: File,
      soughtName: String
  ): Tuple4[Path, Path, Path, Path] = {

    // Create environment used to test both valid and invalid symlinks.

    val dir = top.toPath()
    val d1 = dir.resolve("d1")
    Files.createDirectory(d1)
    assertTrue("a1", Files.exists(d1) && Files.isDirectory(d1))

    // f0 & f1 are just to give find() a bit more complicated case.
    val f0 = d1.resolve("f0")
    val f1 = d1.resolve("f1")
    Files.createFile(f0)
    Files.createFile(f1)

    val d2 = dir.resolve("d2")
    Files.createDirectory(d2)
    assertTrue("a2", Files.exists(d2) && Files.isDirectory(d2))

    val symlinkTarget = d2

    val sought = d2.resolve(soughtName)
    Files.createFile(sought)
    assertTrue("a3", Files.exists(sought) && Files.isRegularFile(sought))

    // Tricky bit here, symlink target is a directory, not a file.
    val symlink = d1.resolve("dirSymlink")
    Files.createSymbolicLink(symlink, symlinkTarget)

    assertTrue("a4", Files.exists(symlink) && Files.isSymbolicLink(symlink))

    (dir, d1, d2, symlink)
  }

  @Test def filesFindRespectsFileVisitOptionFollowLinksValidSymlinks(): Unit = {
    assumeShouldTestSymlinks()

    withTemporaryDirectory { dirFile =>
      val soughtName = "quaesitum" // That which is sought.
      val (dir, d1, d2, symlink) = setupFindSymlinkContext(dirFile, soughtName)

      val predicate = new BiPredicate[Path, BasicFileAttributes] {
        override def test(path: Path, attrs: BasicFileAttributes): Boolean =
          path.getFileName.toString == soughtName
      }

      // Test good symlink when following links.
      val itFollowGood =
        Files.find(d1, 10, predicate, FileVisitOption.FOLLOW_LINKS).iterator

      assertTrue(
        s"Should have found a Path when following symlinks",
        itFollowGood.hasNext
      )

      val foundPath = itFollowGood.next
      val foundName = foundPath.getFileName.toString

      assertEquals("found != sought", soughtName, foundName)

      // Test good symlink when not following links.

      val itNoFollowGood = Files.find(d1, 10, predicate).iterator

      assertEquals(
        s"Should not have found anything when not following symlinks",
        false,
        itNoFollowGood.hasNext
      )
    }
  }

  // Issue #1530
  @Test def filesFindRespectsFileVisitOptionFollowLinksBrokenSymlinks()
      : Unit = {
    assumeShouldTestSymlinks()

    withTemporaryDirectory { dirFile =>
      val soughtName = "quaesitum" // That which is sought.
      val (dir, d1, d2, symlink) = setupFindSymlinkContext(dirFile, soughtName)

      val predicate = new BiPredicate[Path, BasicFileAttributes] {
        override def test(path: Path, attrs: BasicFileAttributes): Boolean =
          path.getFileName.toString == soughtName
      }

      // Break symlink.

      Files.move(d2, dir.resolve("notD2"))
      assertFalse("A1", Files.exists(d2)) // symlink target is gone
      // Now broken symlink itself should continue to exist
      assertTrue("A2.1", Files.exists(symlink, LinkOption.NOFOLLOW_LINKS))
      assertTrue("A3", Files.isSymbolicLink(symlink))

      // Exception should not be thrown
      Files
        .find(d1, 10, predicate, FileVisitOption.FOLLOW_LINKS)
        .iterator
        .hasNext // used to materialize underlying LazyList (since 2.13)

      val itNotFollowBad = Files.find(d1, 10, predicate).iterator

      assertFalse(
        s"Should not have found a Path when following broken symlink",
        itNotFollowBad.hasNext
      )
    }
  }

  @Test def filesGetLastModifiedTimeWorks(): Unit = {
    withTemporaryDirectory { dirFile =>
      val dir = dirFile.toPath()
      val f0 = dir.resolve("f0")

      Files.createFile(f0)
      assertTrue("a1", Files.exists(f0))

      val referenceMs = f0.toFile().lastModified()
      val filetimeMs = Files.getLastModifiedTime(f0).toMillis()

      // Last 3 digits tend to be ignored by JVM
      val lastModifiedResolution = 1000
      assertEquals(
        "a2",
        referenceMs / lastModifiedResolution,
        filetimeMs / lastModifiedResolution
      )
    }
  }

  @Test def filesGetAttributeCanFetchAttributesFromBasicFileAttributeView()
      : Unit = {
    withTemporaryDirectory { dirFile =>
      val d0 = dirFile.toPath()
      val f0 = d0.resolve("f0")

      Files.createFile(f0)
      assertTrue("a1", Files.exists(f0))

      val d0mtime =
        Files.getAttribute(d0, "lastModifiedTime").asInstanceOf[FileTime]
      val d0atime =
        Files.getAttribute(d0, "lastAccessTime").asInstanceOf[FileTime]
      val d0ctime =
        Files.getAttribute(d0, "creationTime").asInstanceOf[FileTime]
      val d0size = Files.getAttribute(d0, "size").asInstanceOf[Long]
      val d0isReg =
        Files.getAttribute(d0, "isRegularFile").asInstanceOf[Boolean]
      val d0isDir = Files.getAttribute(d0, "isDirectory").asInstanceOf[Boolean]
      val d0isSym =
        Files.getAttribute(d0, "isSymbolicLink").asInstanceOf[Boolean]
      val d0isOth = Files.getAttribute(d0, "isOther").asInstanceOf[Boolean]

      assertFalse("a2", d0isReg)
      assertTrue("a3", d0isDir)
      assertFalse("a4", d0isSym)
      assertFalse("a5", d0isOth)

      val f0mtime =
        Files.getAttribute(f0, "lastModifiedTime").asInstanceOf[FileTime]
      val f0atime =
        Files.getAttribute(f0, "lastAccessTime").asInstanceOf[FileTime]
      val f0ctime =
        Files.getAttribute(f0, "creationTime").asInstanceOf[FileTime]
      val f0size = Files.getAttribute(f0, "size").asInstanceOf[Long]
      val f0isReg =
        Files.getAttribute(f0, "isRegularFile").asInstanceOf[Boolean]
      val f0isDir = Files.getAttribute(f0, "isDirectory").asInstanceOf[Boolean]
      val f0isSym =
        Files.getAttribute(f0, "isSymbolicLink").asInstanceOf[Boolean]
      val f0isOth = Files.getAttribute(f0, "isOther").asInstanceOf[Boolean]

      // Last 3 digits tend to be ignored by JVM
      val lastModifiedResolution = 1000
      assertEquals(
        "a6",
        f0mtime.toMillis() / lastModifiedResolution,
        f0.toFile().lastModified() / lastModifiedResolution
      )
      assertEquals("a7", f0size, f0.toFile().length())
      assertTrue("a8", f0isReg)
      assertFalse("a9", f0isDir)
      assertFalse("a10", f0isSym)
      assertFalse("a11", f0isOth)

      /* See Issue #3909
       * fileKey seems to always be null on Windows, even though
       * there is code which appears to return a useful dosFileKey.
       */
      if (!isWindows) {
        /* This fileKey section must change if/when SN ever implements a
         * FileSystem which returns null fileKeys.
         */
        val d0fkey = Files.getAttribute(d0, "fileKey")
        assertNotNull("fileKey: directory", d0fkey)

        val f0fkey1 = Files.getAttribute(f0, "fileKey")
        assertNotNull("fileKey 1: file", f0fkey1)

        // fileKeys for different files be different.
        assertNotEquals("fileKeys should not be equal", d0fkey, f0fkey1)

        val f0fkey2 = Files.getAttribute(f0, "fileKey")
        assertNotNull("fileKey 2: file", f0fkey2)

        /* fileKeys may or may not be reference equal, depending on
         * implementation and possible caching. fileKeys referring to the
         * same file _must_ have content equality.
         */
        assertEquals(
          s"fileKeys should be content equal; key1: ${f0fkey1.toString()}," +
            s" key2: ${f0fkey2.toString()}",
          f0fkey1,
          f0fkey2
        )
      }
    }
  }

  @Test def filesGetAttributeObeysGivenLinkOption(): Unit = {
    withTemporaryDirectory { dirFile =>
      val dir = dirFile.toPath()
      val f0 = dir.resolve("f0")
      val l0 = dir.resolve("l0")

      Files.createFile(f0)
      Files.createSymbolicLink(l0, f0)
      assertTrue("a1", Files.exists(f0))
      assertTrue("a2", Files.exists(l0))

      val normalL0IsReg =
        Files.getAttribute(l0, "isRegularFile").asInstanceOf[Boolean]
      val noFollowL0IsReg = Files
        .getAttribute(l0, "isRegularFile", LinkOption.NOFOLLOW_LINKS)
        .asInstanceOf[Boolean]
      val normalL0IsLink =
        Files.getAttribute(l0, "isSymbolicLink").asInstanceOf[Boolean]
      val noFollowL0IsLink = Files
        .getAttribute(l0, "isSymbolicLink", LinkOption.NOFOLLOW_LINKS)
        .asInstanceOf[Boolean]

      assertTrue("a3", normalL0IsReg)
      assertFalse("a4", noFollowL0IsReg)
      assertFalse("a5", normalL0IsLink)
      assertTrue("a6", noFollowL0IsLink)
    }
  }

  @Test def filesGetAttributeAcceptsViewName(): Unit = {
    withTemporaryDirectory { dirFile =>
      val dir = dirFile.toPath()
      val isDir =
        Files.getAttribute(dir, "basic:isDirectory").asInstanceOf[Boolean]
      assertTrue(isDir)
    }
  }

  @Test def filesGetOwnerWorks(): Unit = {
    withTemporaryDirectory { dirFile =>
      val dir = dirFile.toPath()
      val owner = Files.getOwner(dir)
      assertTrue(owner.getName().nonEmpty)
    }
  }

  @Test def filesGetPosixFilePermissionsWorks(): Unit = {
    assumeFalse("Not testing Posix permissions on Windows", isWindows)
    withTemporaryDirectory { dirFile =>
      val dir = dirFile.toPath()
      val f0 = dir.resolve("f0")
      val f0File = f0.toFile()

      Files.createFile(f0)
      assertTrue(Files.exists(f0))

      f0File.setReadable(true)
      f0File.setWritable(false)
      f0File.setExecutable(true)

      val permissions = Files.getPosixFilePermissions(f0)

      import PosixFilePermission._
      assertTrue("a1", permissions.contains(OWNER_READ))
      assertFalse("a2", permissions.contains(OWNER_WRITE))
      assertTrue("a3", permissions.contains(OWNER_EXECUTE))
    }
  }

  @Test def filesLinesReturnsTheLines(): Unit = {
    withTemporaryDirectory { dirFile =>
      val dir = dirFile.toPath()
      val f0 = dir.resolve("f0")

      val writer = new BufferedWriter(
        new OutputStreamWriter(new FileOutputStream(f0.toFile()))
      )
      writer.write("first line")
      writer.newLine()
      writer.write("second line")
      writer.flush()
      writer.close()

      assertTrue("a1", Files.exists(f0))

      val it = Files.lines(f0).iterator()
      assertTrue("a2", it.next() == "first line")
      assertTrue("a3", it.next() == "second line")
      assertFalse("a4", it.hasNext())
    }
  }

  @Test def filesWriteCanWriteToFile(): Unit = {
    withTemporaryDirectory { dirFile =>
      val dir = dirFile.toPath()
      val f0 = dir.resolve("f0")

      val lines = new Iterable(Array("first line", "second line"))
      Files.write(f0, lines)

      val it = Files.lines(f0).iterator()
      assertTrue("a1", it.next() == "first line")
      assertTrue("a2", it.next() == "second line")
      assertFalse("a3", it.hasNext())
    }
  }

  @Test def filesMoveMovesFiles(): Unit = {
    withTemporaryDirectory { dirFile =>
      val dir = dirFile.toPath()
      val f0 = dir.resolve("f0")
      val f1 = dir.resolve("f1")

      val lines = new Iterable(Array("first line", "second line"))
      Files.write(f0, lines)
      assertTrue("a1", Files.exists(f0))
      assertFalse("a2", Files.exists(f1))
      Files.move(f0, f1)
      assertFalse("a3", Files.exists(f0))
      assertTrue("a4", Files.exists(f1))

      val it = Files.lines(f1).iterator
      assertTrue("a5", it.next() == "first line")
      assertTrue("a6", it.next() == "second line")
      assertFalse("a7", it.hasNext())
    }
  }

  def moveDirectoryTest(
      delete: Boolean,
      populateTarget: Boolean,
      options: CopyOption*
  ): Unit = {
    withTemporaryDirectory { dirFile =>
      val dir = dirFile.toPath()
      val f0 = dir.resolve("f0")
      Files.write(f0, "foo\n".getBytes)
      val target = Files.createTempDirectory(null)

      if (delete) {
        assertTrue(Files.deleteIfExists(target))
      } else if (populateTarget) {
        Files.createFile(target.resolve("ergoSum"))
      }

      Files.move(dir, target, options: _*)
      assertFalse("a1", Files.exists(dir))
      assertFalse("a2", Files.exists(f0))

      val newF0 = target.resolve("f0")
      assertTrue("a3", Files.exists(newF0))
      assertTrue(
        "a4",
        Files.lines(newF0).iterator().toScalaSeq.mkString == "foo"
      )
    }
  }
  @Test def filesMoveDirectory(): Unit = {
    moveDirectoryTest(delete = true, populateTarget = false)
  }

  @Test def filesMoveReplaceDirectory(): Unit = {
    moveDirectoryTest(delete = false, populateTarget = false, REPLACE_EXISTING)
  }

  @Test def filesMoveReplaceDoesNotReplacePopulatedDirectory(): Unit = {
    assertThrows(
      classOf[DirectoryNotEmptyException],
      moveDirectoryTest(delete = false, populateTarget = true, REPLACE_EXISTING)
    )
  }

  @Test def filesMoveDoesNotReplaceDirectory(): Unit = {
    assertThrows(
      classOf[FileAlreadyExistsException],
      moveDirectoryTest(delete = false, populateTarget = false)
    )
  }

  @Test def filesMoveDirectoryCanReplaceDirectory(): Unit = {
    withTemporaryDirectory { dirFile =>
      val dir = dirFile.toPath()
      val file = dir.resolve("f0")
      Files.write(file, "foo\n".getBytes)

      val target = Files.createTempDirectory(null)
      Files.move(file, target, REPLACE_EXISTING)
      assertFalse(
        "Succesfully replaced directory with a file.",
        Files.exists(file)
      )
    }
  }

  @Test def moveBrokenSymlink(): Unit = {
    assumeShouldTestSymlinks()
    withTemporaryDirectory { dirFile =>
      val dir = dirFile.toPath
      val symlink = Files.createSymbolicLink(
        dir.resolve("broken-abs-symlink-1"),
        Paths.get("/does/not/exist")
      )
      val outputDir = Files.createDirectory(dir.resolve("inner-dir"))
      assertTrue(
        Files.exists(
          Files.move(symlink, outputDir.resolve(symlink.getFileName())),
          LinkOption.NOFOLLOW_LINKS
        )
      )
      assertFalse(Files.exists(symlink, LinkOption.NOFOLLOW_LINKS))
    }
  }

  @Test def filesSetAttributeCanSetLastModifiedTime(): Unit = {
    withTemporaryDirectory { dirFile =>
      val dir = dirFile.toPath()
      val f0 = dir.resolve("f0")

      Files.createFile(f0)
      assertTrue(Files.exists(f0))

      val time0 = Files.getAttribute(f0, "lastModifiedTime")
      val time1 = FileTime.fromMillis(1000)
      Files.setAttribute(f0, "lastModifiedTime", FileTime.fromMillis(1000))
      val time2 = Files.getAttribute(f0, "lastModifiedTime")

      assertNotEquals("time0 != time2", time0, time2)
      assertEquals("time1 -- time2", time1, time2)
    }
  }

  @Test def filesSetAttributeCanSetLastAccessTime(): Unit = {
    withTemporaryDirectory { dirFile =>
      val dir = dirFile.toPath()
      val f0 = dir.resolve("f0")

      Files.createFile(f0)
      assertTrue(Files.exists(f0))

      val time0 = Files.getAttribute(f0, "lastAccessTime")
      val time1 = FileTime.fromMillis(1000)
      Files.setAttribute(f0, "lastAccessTime", time1)
      val time2 = Files.getAttribute(f0, "lastAccessTime")

      assertNotEquals("time0 != time2", time0, time2)
      assertEquals("time1 -- time2", time1, time2)
    }
  }

  @Test def filesSetAttributeCanSetPermissions(): Unit = {
    assumeFalse("Not testing Posix permissions on Windows", isWindows)
    withTemporaryDirectory { dirFile =>
      val dir = dirFile.toPath()
      val f0 = dir.resolve("f0")

      Files.createFile(f0)
      assertTrue(Files.exists(f0))

      val perm0 = Files.getAttribute(f0, "posix:permissions")
      val perm1 = PosixFilePermissions.fromString("rwxrwxrwx")
      Files.setAttribute(f0, "posix:permissions", perm1)
      val perm2 = Files.getAttribute(f0, "posix:permissions")

      assertNotEquals("perm0 != perm2", perm0, perm2)
      assertEquals("perm1 -- perm2", perm1, perm2)
    }
  }

  @Test def filesReadAllLinesReturnsAllTheLines(): Unit = {
    withTemporaryDirectory { dirFile =>
      val dir = dirFile.toPath()
      val f0 = dir.resolve("f0")

      val lines = new Iterable(Array("first line", "second line"))
      Files.write(f0, lines)
      assertTrue("exists()", Files.exists(f0))

      val list = Files.readAllLines(f0)
      assertEquals("list.size()", 2, list.size())
      assertEquals("first line", list.get(0))
      assertEquals("second line", list.get(1))
    }
  }

  // Issue I4384, Part1
  @Test def filesReadAllBytesEmptyFile(): Unit = {
    /* Analysis of the reproducer code for the first reported defect
     * in SN Issue 4348  shows that the root cause is that the
     * file corresponding to the given path is empty and not that
     * particularly that it is a special file.
     * The unix path below uses '/dev/null' from the Issue to keep this
     * test close to the reported issue.
     */

    val emptyDeviceName =
      if (!isWindows) "/dev/null"
      else "NUL"

    val emptyPath = Paths.get(emptyDeviceName)

    val bytesRead = Files.readAllBytes(emptyPath)

    assertEquals("bytes read", 0, bytesRead.size)
  }

  @Test def filesReadAllBytesReadsAllBytes(): Unit = {
    withTemporaryDirectory { dirFile =>
      val dir = dirFile.toPath()
      val f0 = dir.resolve("f0")
      val in = new ByteArrayInputStream(Array(1, 2, 3))
      Files.copy(in, f0)
      assertTrue("exists()", Files.exists(f0))
      assertEquals("Files.size(f0)", 3, Files.size(f0))

      val bytes = Files.readAllBytes(f0)
      assertEquals("bytes(0)", 1, bytes(0))
      assertEquals("bytes(1)", 2, bytes(1))
      assertEquals("bytes(2)", 3, bytes(2))
    }
  }

  @Test def filesReadAttributesPathClassArrayLinkOptionWorks(): Unit = {
    withTemporaryDirectory { dirFile =>
      val dir = dirFile.toPath
      val attrs = Files.readAttributes(dir, classOf[BasicFileAttributes])
      assertTrue("a1", attrs.isDirectory())
      assertFalse("a2", attrs.isOther())
      assertFalse("a3", attrs.isRegularFile())
      assertFalse("a4", attrs.isSymbolicLink())
    }
  }

  @Test def filesReadAttributesPathStringArrayLinkOptionWorks(): Unit = {
    withTemporaryDirectory { dirFile =>
      val dir = dirFile.toPath
      val attrs = Files.readAttributes(dir, "basic:isRegularFile,size")
      assertEquals("attrs.size", 2, attrs.size)
      assertTrue("a1", attrs.containsKey("isRegularFile"))
      assertTrue("a2", attrs.containsKey("size"))
    }
  }

  @Test def filesReadAttributesPathStringArrayLinkOptionSupportsAsterisk()
      : Unit = {
    withTemporaryDirectory { dirFile =>
      val dir = dirFile.toPath
      val attrs = Files.readAttributes(dir, "basic:*")
      assertFalse(attrs.isEmpty())
    }
  }

  @Test def filesReadAttributesThrowsOnBrokenSymbolicLink(): Unit = {
    // Does fail on Windows. Cannot open broken link
    assumeShouldTestSymlinks()

    withTemporaryDirectory { dirFile =>
      val dir = dirFile.toPath()
      val brokenLink = dir.resolve("link")
      val file = dir.resolve("file")
      Files.createSymbolicLink(brokenLink, file)
      assertThrows(
        classOf[NoSuchFileException],
        Files.readAttributes(brokenLink, classOf[BasicFileAttributes])
      )
    }
  }

  @Test def filesReadAttributesOnSymbolicLinks(): Unit = {
    assumeShouldTestSymlinks()

    withTemporaryDirectory { dirFile =>
      val dir = dirFile.toPath()
      val dir2 = Files.createDirectories(dir.resolve("dir2"))
      val file = Files.createFile(dir.resolve("file"))
      val link1 = Files.createSymbolicLink(dir.resolve("link1"), file)
      val link2 = Files.createSymbolicLink(dir.resolve("link2"), link1)
      val link3 =
        Files.copy(link2, dir.resolve("link3"), LinkOption.NOFOLLOW_LINKS)
      val link4 =
        Files.createSymbolicLink(dir.resolve("link4"), Paths.get(".", "link2"))
      val link5 =
        Files.createSymbolicLink(
          dir2.resolve("link5"),
          Paths.get("..", "link2")
        )
      val link6 = Files.createSymbolicLink(dir.resolve("link6"), link5)
      Seq(file, link1, link2, link3, link4, link5, link6).foreach { path =>
        assertTrue(s"exists $path", Files.exists(path))
        val attrsFollow =
          Files.readAttributes(path, classOf[BasicFileAttributes])
        val attrsNoFollow = Files.readAttributes(
          path,
          classOf[BasicFileAttributes],
          LinkOption.NOFOLLOW_LINKS
        )
        assertTrue(s"isFile followLinks $path", attrsFollow.isRegularFile())
        assertEquals(
          s"isFile no-followLinks $path",
          path == file,
          attrsNoFollow.isRegularFile()
        )

        assertFalse(
          s"isSymLink followLinks $path",
          attrsFollow.isSymbolicLink()
        )
        assertEquals(
          s"isSymLink no-followLinks $path",
          path != file,
          attrsNoFollow.isSymbolicLink()
        )
      }
    }
  }

  @Test def filesNewByteChannelReturnsChannel(): Unit = {
    withTemporaryDirectory { dir =>
      val f = dir.toPath.resolve("f0")
      Files.write(f, Array[Byte](1, 2, 3))
      val channel = Files.newByteChannel(f)
      val buffer = ByteBuffer.allocate(10)

      val read = channel.read(buffer)
      buffer.flip()

      assertEquals("buffer.limit()", 3, buffer.limit())
      assertEquals("read", 3, read)
      assertEquals("buffer.get(0)", 1, buffer.get(0))
      assertEquals("buffer.get(1)", 2, buffer.get(1))
      assertEquals("buffer.get(2)", 3, buffer.get(2))
    }
  }

  @Test def newInputStreamReturnsAnInputStream(): Unit = {
    withTemporaryDirectory { dirFile =>
      val dir = dirFile.toPath
      val f0 = dir.resolve("f0")
      Files.write(f0, Array[Byte](1, 2, 3))
      assertTrue("file does not exist", Files.exists(f0))

      val in = Files.newInputStream(f0)
      assertEquals("read #1", 1, in.read())
      assertEquals("read #2", 2, in.read())
      assertEquals("read #3", 3, in.read())
      assertEquals("read #4", -1, in.read())
    }
  }

  @Test def newOutputStreamReturnsAnOutputStream(): Unit = {
    withTemporaryDirectory { dirFile =>
      val dir = dirFile.toPath
      val f0 = dir.resolve("f0")
      val out = Files.newOutputStream(f0)

      assertTrue("exists()", Files.exists(f0))

      out.write(Array[Byte](1, 2, 3))

      val in = Files.newInputStream(f0)
      assertEquals("read #1", 1, in.read())
      assertEquals("read #2", 2, in.read())
      assertEquals("read #3", 3, in.read())
      assertEquals("read #4", -1, in.read())
    }
  }

  @Test def newOutputStreamHonorsOpenOptions(): Unit = {
    withTemporaryDirectory { dirFile =>
      val dir = dirFile.toPath
      val f0 = dir.resolve("f0")

      val out0 = Files.newOutputStream(f0)
      out0.write(Array[Byte](1, 2, 3))
      out0.close()

      val out1 =
        Files.newOutputStream(f0, StandardOpenOption.TRUNCATE_EXISTING)
      out1.close()

      val in0 = Files.newInputStream(f0)
      assertEquals("in0.read()", -1, in0.read())

      val f1 = dir.resolve("f1")
      Files.createFile(f1)
      assertThrows(
        classOf[FileAlreadyExistsException],
        Files.newOutputStream(f1, StandardOpenOption.CREATE_NEW)
      )

      val f2 = dir.resolve("f2")
      assertThrows(
        classOf[NoSuchFileException],
        Files.newOutputStream(f2, StandardOpenOption.WRITE)
      )
    }
  }

  @Test def setPosition(): Unit = {
    val offset = 42
    val target = Files.createTempFile("", "")
    val openAttrs = new java.util.HashSet[OpenOption]

    val out = Files.newByteChannel(target, openAttrs)
    assertNotNull(out)
    try {
      assertEquals(0, out.position())
      out.position(offset)
      assertEquals(offset, out.position())
    } finally out.close()
  }

  private class FollowLinksTestsContext(dirPath: Path) {

    final val fNames =
      Array(
        "missingtarget",
        "A0", // sort before "b" in "brokenlink"
        "z99" // sort after "m" in "missingtarget"
      )

    for (i <- 0 until fNames.length) {
      val f = dirPath.resolve(fNames(i))
      Files.createFile(f)
      assertTrue(Files.exists(f) && Files.isRegularFile(f))
    }
    val brokenLinkName = "brokenlink"
    val brokenLink = dirPath.resolve(brokenLinkName)
    val missingTarget = dirPath.resolve(fNames(0))

    // Create valid symbolic link from brokenLink to missingTarget,
    // then remove missingTarget to break link.
    // This could probably be done in one step, but use two to avoid
    // filesystem optimizations and to more closely emulate what happens
    // in the real world.

    Files.createSymbolicLink(brokenLink, missingTarget)

    assertTrue(
      s"File '${brokenLink}' does not exist or is not a symbolic link.",
      Files.exists(brokenLink) && Files.isSymbolicLink(brokenLink)
    )

    Files.delete(missingTarget)

    assertFalse(
      s"File '${missingTarget}' should not exist.",
      Files.exists(missingTarget)
    )

    def expectedFollowFilesSet(): Set[String] =
      fNames.drop(1).toSet + brokenLinkName
  }

  private def visitorToFileNamesSet(v: QueueingVisitor): Set[String] = {
    v.dequeue() // skip temp directory pre-visit.

    // -1 to skip temp directory post-visit
    val nStrings = v.length() - 1
    val strings = new Array[String](nStrings)

    for (i <- 0 until nStrings) {
      strings(i) = v.dequeue().getFileName.toString
    }

    strings.toSet
  }
}

object FilesTest {

  def cleanupWorkArea(dirPath: Path, paths: Path*): Unit = {
    /* If execution reaches here, Tests have been successful so there is no
     * need to preserve created directory & files for analysis.
     */

    val donePermissions = PosixFilePermissions.fromString("rwx------")
    // Assume dirPath has appropriate ownership & user write protection.
    for (path <- paths) {
      Files.setPosixFilePermissions(path, donePermissions)
      Files.deleteIfExists(path)
    }

    Files.deleteIfExists(dirPath)
  }

  // Paths are more useful than io.File for java.nio.file.Files* methods.
  def makeTemporaryDirPath(): Path = {
    val prefix = "scala-native-testsuite_javalib-FilesTest"
    Files.createTempDirectory(prefix)
  }

  // name use historically and widely in this file.
  def makeTemporaryDir(): File =
    makeTemporaryDirPath().toFile()

  def withTemporaryDirectory(fn: File => Unit): Unit =
    fn(makeTemporaryDir())

  def withTemporaryDirectoryPath(fn: Path => Unit): Unit =
    fn(makeTemporaryDirPath())
}

class Iterable[T](elems: Array[T]) extends java.lang.Iterable[T] {
  override val iterator = new java.util.Iterator[T] {
    private var i = 0
    override def hasNext(): Boolean = i < elems.length
    override def next(): T =
      if (hasNext()) {
        val elem = elems(i)
        i += 1
        elem
      } else throw new NoSuchElementException()
    override def remove(): Unit =
      throw new UnsupportedOperationException()
  }
}

class QueueingVisitor extends SimpleFileVisitor[Path] {
  private val visited = scala.collection.mutable.Queue.empty[Path]
  def isEmpty(): Boolean = visited.isEmpty
  def dequeue(): Path = visited.dequeue()
  def length() = visited.length

  override def visitFileFailed(
      file: Path,
      error: IOException
  ): FileVisitResult =
    throw error

  override def preVisitDirectory(
      dir: Path,
      attributes: BasicFileAttributes
  ): FileVisitResult = {
    visited.enqueue(dir)
    FileVisitResult.CONTINUE
  }

  override def postVisitDirectory(
      dir: Path,
      error: IOException
  ): FileVisitResult = {
    visited.enqueue(dir)
    FileVisitResult.CONTINUE
  }

  override def visitFile(
      file: Path,
      attributes: BasicFileAttributes
  ): FileVisitResult = {
    visited.enqueue(file)
    FileVisitResult.CONTINUE
  }
}

/* Implement a FileVisitor which emulates the transfomation(s)
 * in Li Haoyi's os-lib os.walk() call. That is, the starting
 * directory Path name is skipped but all other directory Path names
 * are added _before_ their contents are visited.
 *
 * This allows to keep number and order of files reported in the
 * 'filesWalkFileTreeMaxDepthPositive()' Test the same as in
 * reported issue. This makes it easier to correlate Issue & Test.
 */

class OsLibLikeQueueingVisitor extends SimpleFileVisitor[Path] {
  private val visited = scala.collection.mutable.Queue.empty[Path]
  def isEmpty(): Boolean = visited.isEmpty
  def dequeue(): Path = visited.dequeue()
  def length() = visited.length

  var dirCount = 0

  override def preVisitDirectory(
      dir: Path,
      attributes: BasicFileAttributes
  ): FileVisitResult = {
    if (dirCount == 0) dirCount += 1
    else visited.enqueue(dir)
    FileVisitResult.CONTINUE
  }

  override def visitFile(
      file: Path,
      attributes: BasicFileAttributes
  ): FileVisitResult = {
    visited.enqueue(file)
    FileVisitResult.CONTINUE
  }
}

// WalkFileTreeQueueingVisitor
class WftQueueingVisitor extends SimpleFileVisitor[Path] {
  private val visited = scala.collection.mutable.Queue.empty[Path]
  def dequeue(): Path = visited.dequeue()
  def isEmpty(): Boolean = visited.isEmpty
  def lastOption(): Option[Path] = visited.lastOption
  def length() = visited.length

  override def preVisitDirectory(
      dir: Path,
      attributes: BasicFileAttributes
  ): FileVisitResult = {
    visited.enqueue(dir)
    FileVisitResult.CONTINUE
  }

  override def visitFile(
      file: Path,
      attributes: BasicFileAttributes
  ): FileVisitResult = {
    visited.enqueue(file)
    FileVisitResult.CONTINUE
  }
}
