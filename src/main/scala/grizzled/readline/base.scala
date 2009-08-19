/*
  ---------------------------------------------------------------------------
  This software is released under a BSD license, adapted from
  http://opensource.org/licenses/bsd-license.php

  Copyright (c) 2009, Brian M. Clapper
  All rights reserved.

  Redistribution and use in source and binary forms, with or without
  modification, are permitted provided that the following conditions are
  met:

  * Redistributions of source code must retain the above copyright notice,
    this list of conditions and the following disclaimer.

  * Redistributions in binary form must reproduce the above copyright
    notice, this list of conditions and the following disclaimer in the
    documentation and/or other materials provided with the distribution.

  * Neither the names "clapper.org", "Grizzled Scala Library", nor the
    names of its contributors may be used to endorse or promote products
    derived from this software without specific prior written permission.

  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
  IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
  THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
  PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR
  CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
  EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
  PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
  PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
  LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
  NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
  SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
  ---------------------------------------------------------------------------
*/

package grizzled.readline

/**
 * Models a Readline history: an object that holds previously read
 * lines.
 */
trait History
{
    /**
     * Get maximum history size.
     *
     * @return the current max history size, or 0 for unlimited.
     */
    def max: Int

    /**
     * Set maximum history size.
     *
     * @param newSize the new max history size, or 0 for unlimited.
     */
    def max_=(newSize: Int)

    /**
     * Add a line to the history. Does not add the line if it is
     * identical to the most recently added line.
     *
     * @param line  the line to add
     */
    def +=(line: String) =
    {
        last match
        {
            case None    => append(line)
            case Some(s) => if (s != line) append(line)
        }
    }

    /**
     * Get the current number of entries in the history buffer.
     *
     * @return the size of the history buffer
     */
    def size: Int

    /**
     * Get the last (i.e., most recent) entry from the buffer.
     *
     * @return the most recent entry, as an <tt>Option</tt>, or
     *         <tt>None</tt> if the history buffer is empty
     */
    def last: Option[String]

    /**
     * Get the contents of the history buffer, in a list.
     *
     * @return the history lines
     */
    def get: List[String]

    /**
     * Clear the history buffer
     */
    def clear

    /**
     * Save the contents of the history to the specified path.
     *
     * @param path  where to save the history
     */
    def save(path: String) =
    {
        import _root_.java.io.FileWriter

        val f = new FileWriter(path)
        for (line <- get)
            f.write(line + "\n")
        f.close
    }

    /**
     * Load the contents of the history from the specified path, overwriting
     * any existing history data (i.e., clearing the history buffer first).
     *
     * @param path  where to save the history
     */
    def load(path: String) =
    {
        import _root_.java.io.{FileReader,
                               FileNotFoundException,
                               BufferedReader}

        try
        {
            val f = new BufferedReader(new FileReader(path))
            clear

            def readHistoryLine: Unit =
            {
                val line = f.readLine
                if (line != null)
                {
                    this += line
                    readHistoryLine
                }
            }

            readHistoryLine
            f.close
        }

        catch
        {
            case _: FileNotFoundException =>
        }
    }

    /**
     * Unconditionally appends the specified line to the history.
     *
     * @param line  the line to add
     */
    protected def append(line: String)
}

sealed abstract class CompletionToken;
case class LineToken(val value: String) extends CompletionToken;
case object Delim extends CompletionToken;
case object Cursor extends CompletionToken;

/**
 * Models a completer: An object that, given a line of input and a token
 * within that line, finds possible completions for the token.
 */
trait Completer
{
    /**
     * Get all completions for a token. The <tt>context</tt> argument bears
     * some explaining. It's designed to allow the completer to locate the
     * cursor (and the nearest token) via Scala pattern matching, and it
     * consists of a stream of abstract tokens:
     *
     * <ul>
     *  <li><tt>LineToken</tt> is a token parsed from the line, in an object
     *      that's similar to <tt>Some</tt>: <tt>LineToken.value</tt> returns
     *      the token's string.
     *  <li><tt>Cursor</tt> indicates the location of the cursor
     *  <li><tt>Delim</tt> indicates the presence of a token delimiter
     *      (typically white space)
     * </ul>
     *
     * The input line is broken into these tokens, which can then be matched.
     * For example, consider the following input lines, with the cursor where
     * the caret is:
     *
     * <table border="0">
     *   <tr valign="top">
     *     <th>Input</th>
     *     <th>Tokens</th>
     *   </tr>
     *
     *   <tr valign="top">
     *     <td><tt>^</tt>
     *     <td><tt>Cursor</tt></td>
     *   </tr>
     *
     *   <tr valign="top">
     *     <td><tt>cm^</tt>
     *     <td><tt>LineToken("cm") Cursor</tt></td>
     *   </tr>
     *
     *   <tr valign="top">
     *     <td><tt>cmd ^</tt>
     *     <td><tt>LineToken("cmd") Delim Cursor</tt></td>
     *   </tr>
     *
     *   <tr valign="top">
     *     <td><tt>cmd arg1^</tt>
     *     <td><tt>LineToken("cmd") Delim Token("arg1") Cursor</tt></td>
     *   </tr>
     *
     *   <tr valign="top">
     *     <td><tt>cmd arg1^</tt>
     *     <td><tt>LineToken("cmd") Delim Token("arg1") Delim Cursor</tt></td>
     *   </tr>
     *
     *   <tr valign="top">
     *     <td><tt>cmd^ arg1</tt>
     *     <td><tt>LineToken("cmd") Cursor Delim Token("arg1")</tt></td>
     *   </tr>

     *   <tr valign="top">
     *     <td><tt>cm^d arg1</tt>
     *     <td><tt>LineToken("cmd") Cursor Delim Token("arg1")</tt></td>
     *   </tr>
     * </table>
     *
     * @param token    the token being completed
     * @param context  the token context
     * @param line     the current unparsed input line, which includes the token
     *
     * @return a list of completions, or Nil if there are no matches
     */
    def complete(token: String, 
                 context: List[CompletionToken], 
                 line: String): List[String]
}

private[readline] trait CompleterHelper
{
    /**
     * Helper method that takes a set of tokens (of whatever type) and
     * converts them into <tt>LineToken</tt> objects with <tt>Delim</tt>
     * objects in between. To get the string to put in the <tt>LineToken</tt>
     * objects, this method uses the <tt>toString()</tt> method on each passed
     * token.
     *
     * @param tokens  the tokens to map
     *
     * @return a list of <tt>CompletionToken</tt> objects consisting of the
     *         tokens, as strings, with intervening <tt>Delim</tt> characters
     */
    def mapWithDelims(tokens: List[Any]): List[CompletionToken] =
    {
        // Create (LineToken, Delim) pairs...
        tokens.flatMap(t => List(LineToken(t.toString), Delim))
        // ... and drop last Delim
              .reverse.drop(1).reverse
    }
}

/**
 * A completer that doesn't do anything. Useful as a default.
 */
class NullCompleter extends Completer
{
    def complete(token: String, 
                 context: List[CompletionToken], 
                 line: String): List[String] = Nil
}

/**
 * A completer that completes path names. Handles "~" expansion, but only
 * for the current user.
 */
class PathnameCompleter extends Completer
{
    def complete(token: String, 
                 context: List[CompletionToken], 
                 line: String): List[String] =
    {
        import grizzled.file.{util => FileUtil}
        import java.io.File

        val strippedToken = token.trim
        val expandedToken =
            if (strippedToken.startsWith("~"))
            {
                val userHome = System.getProperty("user.home")
                if ((strippedToken.length > 1) && 
                    (strippedToken(1) != File.separatorChar))
                    // Can't expand ~user
                    strippedToken

                else if (userHome == null)
                    strippedToken

                else
                    userHome + strippedToken.drop(1)
            }
            else
                strippedToken

        val (directory, filename, includeDirectory) =
            if (expandedToken.length == 0)
                (".", None, false)
            else if (! (expandedToken contains File.separator))
                (".", Some(expandedToken), false)
            else if (new File(expandedToken).isDirectory)
                (expandedToken, None, true)
            else if (expandedToken endsWith File.separator)
                (expandedToken, None, true)
            else
                (FileUtil.dirname(expandedToken),
                 Some(FileUtil.basename(expandedToken)),
                 true)

        if (directory.trim == "")
            Nil

        else
        {
            val fDir = new File(directory)
            val files = fDir.list.filter(s => (! s.startsWith(".")))
            val matches = filename match
            {
                case Some(f) =>
                    files.filter(s => s.startsWith(f))
                case None =>
                    files
            }

            if (includeDirectory)
                matches.map(s => FileUtil.joinPath(fDir.getPath, s)).toList
            else
                matches.toList
        }
    }
}

/**
 * A completer that completes from a list of items.
 *
 * @param completions  the list of valid completions
 * @param convert      function to convert both the token to be compared
 *                     and the candidate completion token, before comparing
 *                     them (e.g., by converting them to lower case)
 */
class ListCompleter(val completions: List[String],
                    val convert: (String) => String) extends Completer
{
    /**
     * Version of the constructor that uses a default, no-op <tt>convert()</tt>
     * function.
     *
     * @param completions  the list of valid completions
     */
    def this(completions: List[String]) = this(completions, (s: String) => s)

    def complete(token: String, 
                 context: List[CompletionToken], 
                 line: String): List[String] =
    {
        if (token == "")
            completions
        else
            completions.filter((s) => convert(s).startsWith(convert(token)))
    }
}

/**
 * Defines the readline-like functionality supported by this API. A
 * <tt>Readline</tt> class provides:
 *
 * <ul>
 *   <li> a means to read lines of input from (presumably) a terminal
 *   <li> a history mechanism
 *   <li> an optional tab-completion capability
 * </ul>
 */
trait Readline
{
    /**
     * A printable name for the implementation.
     */
    val name: String

    /**
     * The completer, if any.
     */
    var completer: Completer = new NullCompleter

    /**
     * The history buffer. The actual implementation depends on the underlying
     * class.
     */
    val history: History

    /**
     * Whether or not to add lines to the history automatically.
     */
    val autoAddHistory: Boolean

    /**
     * Read a line of input from the console.
     *
     * @param prompt  the prompt to display before reading.
     *
     * @return An <tt>Option</tt> containing the line (e.g., <tt>Some(...)</tt>)
     *         or <tt>None</tt> on EOF.
     */
    def readline(prompt: String): Option[String] =
    {
        val line = doReadline(prompt)
        if ((line != None) && autoAddHistory)
            history += line.get
            
        line
    }

    /**
     * The actual function that does the readline work, provided by the
     * concrete implementing class.
     *
     * @param prompt  the prompt to display before reading.
     *
     * @return An <tt>Option</tt> containing the line (e.g., <tt>Some(...)</tt>)
     *         or <tt>None</tt> on EOF.
     */
    private[readline] def doReadline(prompt: String): Option[String]

    /**
     * Produce a readable version of this object.
     */
    override def toString = name
}

/**
 * Companion factory object, used to instantiate particular readline
 * implementations.
 */
object Readline
{
    /**
     * An enumeration of the various underlying readline APIs supported by
     * this API. Note that a given API may or may not be available on a
     * particular machine. The following implementations are currently
     * supported:
     *
     * <ul>
     *   <li><tt>GNUReadline</tt>: The GNU Readline library. Requires the
     *       JavaReadline jar
     *       (<a href="http://java-readline.sourceforge.net/">http://java-readline.sourceforge.net/</a>)
     *       and the GNU Readline library
     *       (<a href="http://tiswww.case.edu/php/chet/readline/rltop.html">http://tiswww.case.edu/php/chet/readline/rltop.html</a>).
     *
     *   <li><tt>Editline</tt>: The Editline library, originally from BSD Unix.
     *       Requires the JavaReadline jar
     *       (<a href="http://java-readline.sourceforge.net/">http://java-readline.sourceforge.net/</a>)
     *       and the Editline library
     *       <a href="http://www.thrysoee.dk/editline/">http://www.thrysoee.dk/editline/</a>.
     *
     *   <li><tt>Getline</tt>: The Getline library. Requires the JavaReadline jar
     *       (<a href="http://java-readline.sourceforge.net/">http://java-readline.sourceforge.net/</a>)
     *       and the Getline library.
     *
     *   <li><tt>JLine</tt>: The JLine library. Requires the JLine jar
     *       (<a href="http://jline.sourceforge.net/">http://jline.sourceforge.net/</a>).
     *   <li><tt>Simple</tt>: A simple, not-editing, pure Java implementation
     * </ul>
     */
    object ReadlineType extends Enumeration
    {
        type ReadlineType = Value

        val GNUReadline = Value
        val EditLine = Value
        val GetLine = Value
        val JLine = Value
        val Simple = Value
    }

    import ReadlineType._

    /**
     * Get the specified <tt>Readline</tt> implementation.
     *
     * @param readlineType   the <tt>ReadlineType</tt> to use
     * @param appName        an arbitrary name of the calling application
     * @param autoAddHistory whether lines read by the function should
     *                       automatically be added to the history. If this
     *                       parameter is <tt>false</tt>, then the caller
     *                       is responsible for adding lines to the history.
     *
     * @return the appropriate <tt>Readline</tt> implementation.
     *
     * @throws UnsatisfiedLinkError can't find the underlying library
     */
    def apply(readlineType: ReadlineType,
              appName: String,
              autoAddHistory: Boolean): Readline =
    {
        val cls = readlineType match
        {
            case GNUReadline =>
                Class.forName("grizzled.readline.javareadline.GNUReadlineImpl")

            case EditLine =>
                Class.forName("grizzled.readline.javareadline.EditlineImpl")

            case GetLine =>
                Class.forName("grizzled.readline.javareadline.GetlineImpl")

            case JLine =>
                Class.forName("grizzled.readline.jline.JLineImpl")

            case Simple =>
                Class.forName("grizzled.readline.simple.SimpleImpl")
        }

        val constructor = cls.getConstructor(classOf[String], classOf[Boolean])
        val histFlagParam = autoAddHistory.asInstanceOf[Object]
        try
        {
            val rl = constructor.newInstance(appName, histFlagParam)
            rl.asInstanceOf[Readline]
        }

        catch
        {
            case e: java.lang.reflect.InvocationTargetException =>
                throw e.getCause
        }
    }

    /**
     * Get the specified <tt>Readline</tt> implementation, with
     * <tt>autoAddHistory</tt> set to <tt>true</tt>.
     *
     * @param readlineType  the <tt>ReadlineType</tt> to use
     * @param appName       an arbitrary name of the calling application
     *
     * @return the appropriate <tt>Readline</tt> implementation.
     *
     * @throws UnsatisfiedLinkError can't find the underlying library
     */
    def apply(readlineType: ReadlineType, appName: String): Readline =
        apply(readlineType, appName, true)
}