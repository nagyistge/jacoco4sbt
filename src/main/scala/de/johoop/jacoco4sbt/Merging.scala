/*
 * This file is part of jacoco4sbt.
 * 
 * Copyright (c) 2011-2013 Joachim Hofer & contributors
 * All rights reserved.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package de.johoop.jacoco4sbt

import sbt._

trait Merging extends JaCoCoRuntime {
  import org.jacoco.core.data.{ExecutionDataWriter, ExecFileLoader}
  import java.io._
  import sbt.Keys._

  def conditionalMergeAction(jacocoDirectory: File, streams: TaskStreams, mergeReports: Boolean) = {
    if (mergeReports) mergeAction(jacocoDirectory, streams)
    else streams.log debug "Not merging execution data!"
  }

  def mergeAction(jacocoDirectory: File, streams: TaskStreams) = {
    val parent = jacocoDirectory.getParentFile

    import FileSeq._
    val execs = parent.walkWithFilter(_.getName == "jacoco.exec")
    streams.log debug ("Found data files: %s" format execs.map(_.getAbsolutePath).mkString(","))

    val loader = new ExecFileLoader
    execs foreach loader.load

    val mergedFile = new File(jacocoDirectory, "jacoco-merged.exec")
    streams.log debug ("Writing merged data to: %s" format mergedFile.getAbsolutePath)
    writeToFile(mergedFile) { outputStream =>
      val dataWriter = new ExecutionDataWriter(outputStream)
      loader.getSessionInfoStore accept dataWriter
      loader.getExecutionDataStore accept dataWriter
    }
  }

  private def writeToFile(f: File)(writeFn: OutputStream => Unit) = {
    try {
      val out = new BufferedOutputStream(new FileOutputStream(f))
      try writeFn(out)
      catch {
        case e: IOException => throw new ResourcesException("Error merging Jacoco files: %s" format e.getMessage)
      } finally out.close
    } catch {
      case e: IOException => 
        throw new ResourcesException("Unable to write out Jacoco file during merge: %s" format e.getMessage)
    }
  }
}

// Helper classes to easily walk through trees
object FileSeq {
  def apply(dir:File) = new FileSeq(dir.listFiles().toList)

  // implicit conversion of File to FileSeq
  implicit def file2FileSeq(dir:File):FileSeq = FileSeq(dir)

  class FileSeq(override val files:List[File]) extends GenFileList(files)

  import collection.GenSeq
  class GenFileList(val files:GenSeq[File]) {
    private def fileOrSubDir: (File) => GenSeq[File] = f => if (f.isFile) GenSeq(f) else f.walk

    def walk:GenSeq[File] = {
      files.flatMap(fileOrSubDir)
    }

    def walkWithFilter(fileFilter: File => Boolean):GenSeq[File] = {
      files.filter(f => f.isDirectory || fileFilter(f)).flatMap(fileOrSubDir).filter(fileFilter)
    }
  }
}