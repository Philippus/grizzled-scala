package grizzled.net

import java.io.InputStream
import java.net.{URL => JavaURL}

import scala.util.Try

/** Convenient Scala case-class wrapper for a `java.net.URL`. This class
  * doesn't include all the capabilities. For example, it lacks the equivalent
  * of `getContent()`, as that's better handled through other means.
  *
  * @param protocol   the protocol, if defined
  * @param host       the host, if defined
  * @param port       the port, if defined
  * @param path       the path
  * @param query      the query string, if any
  * @param userInfo   the URL's user info, if any
  * @param fragment   the fragment, if any
  */
case class URL(protocol: String,
               host:     Option[String],
               port:     Option[Int],
               path:     Option[String],
               query:    Option[String] = None,
               userInfo: Option[String] = None,
               fragment: Option[String] = None) {

  /** The underlying `java.net.URL`.
    */
  val javaURL = {
    // Get around some really odd Java URL issues by simply creating a
    // URL, then mapping it to a URL. Except that this doesn't work with
    // "jar" URLs.
    protocol match {
      case "http" | "https" | "file" | "ftp" =>
        URI(scheme   = Some(protocol),
            userInfo = userInfo,
            host     = host,
            port     = port,
            path     = path,
            query    = query,
            fragment  = fragment).javaURI.toURL
      case _ =>
        new JavaURL(protocol, host.orNull, port.getOrElse(-1), path.orNull)
    }
  }


  /** The coded authority for this URI.
    *
    * @return the authority, if any
    */
  def authority = Option(javaURL.getAuthority)

  /** Get the default port for the protocol.
    *
    * @return the default port
    */
  val defaultPort = {
    val port = javaURL.getDefaultPort
    if (port < 0) None else Some(port)
  }

  /** Open an input stream to the URL.
    *
    * @return `Success(stream)` or `Failure(Exception)`
    */
  def openStream(): Try[InputStream] = Try {
    javaURL.openStream()
  }

  /** Get the URL string representation of this URL (i.e., the string
    * you could paste into a browser). Contrast this function with
    * `toString()`, which gets the string representation of the object
    * and its fields.
    *
    * @return the string
    */
  def toExternalForm = javaURL.toExternalForm

}

/** Companion object, adding some functions that aren't available in the
  * generated one.
  */
object URL {
  /** Construct a URL from a `java.net.URL`.
    *
    * @param url the `java.net.URL`
    */
  def apply(url: java.net.URL): URL = {
    URL(protocol = url.getProtocol,
        host     = Option(url.getHost).filter(_.length > 0),
        port     = if (url.getPort < 0) None else Some(url.getPort),
        path     = Option(url.getPath).filter(_.length > 0),
        userInfo = Option(url.getUserInfo).filter(_.length > 0),
        query    = Option(url.getQuery).filter(_.length > 0),
        fragment = Option(url.getRef).filter(_.length > 0))
  }

  /** Construct a URL from a string.
    *
    * @param spec the string specification
    * @return `Success(URL)` or `Failure(Exception)`
    */
  def apply(spec: String): Try[URL] = Try { URL(new java.net.URL(spec)) }
}
