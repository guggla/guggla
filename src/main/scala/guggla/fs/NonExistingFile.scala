package guggla.fs

import scala.tools.nsc.io.AbstractFile
import java.io.{ OutputStream, InputStream }

object NonExistingFile extends AbstractFile {
  def name = null
  def path = null
  def absolute = this
  def container = null
  def file = null
  def create { unsupported }
  def delete { unsupported }
  def isDirectory = false
  def lastModified = 0
  def input: InputStream = null
  def output: OutputStream = null
  def iterator: Iterator[AbstractFile] = Iterator.empty
  def lookupName(name: String, directory: Boolean) = null
  def lookupNameUnchecked(name: String, directory: Boolean) = this
  override def exists = false
}
