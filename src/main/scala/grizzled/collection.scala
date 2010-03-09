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

/**
 * Some collection-related helpers.
 */
package grizzled.collection

import scala.collection.generic._
import java.util.{Collection, Iterator => JIterator}

/**
 * Useful for converting a collection into an object suitable for use with
 * Scala's <tt>for</tt> loop.
 */
class CollectionIterator[T](val iterator: JIterator[T]) extends Iterator[T] {

    /**
     * Alternate constructor that takes a collection.
     *
     * @param collection  the collection
     */
    def this(collection: Collection[T]) = this(collection.iterator)

    def hasNext: Boolean = iterator.hasNext
    def next: T = iterator.next
}

/**
 * An <tt>Iterator</tt> for lists.
 */
class ListIterator[+T](val list: List[T]) extends Iterator[T] {

    private var cursor = 0

    def hasNext: Boolean = cursor < list.length
    def next: T = {
        val result = list(cursor)
        cursor += 1
        result
    }
}

/**
 * An iterator that iterates, serially, over the contents of multiple other
 * iterators.
 *
 * @param iterators  the iterators to wrap
 */
class MultiIterator[+T](iterators: Iterator[T]*) extends Iterator[T] {

    private val iteratorList: List[Iterator[T]] = iterators.toList
    private var cursor = 0

    /**
     * Determines whether the iterator is empty. A <tt>MultiIterator</tt>
     * is empty when all contained iterators have been exhausted.
     *
     * @return <tt>true</tt> if there's more to read, <tt>false</tt> if not
     */
    def hasNext: Boolean = {
        if (cursor >= iteratorList.length)
            false

        else if (iteratorList(cursor).hasNext)
            true

        else {
            cursor += 1
            hasNext
        }
    }

    /**
     * Get the next element.
     *
     * @return the next element
     */
    def next: T = {
        if (! hasNext)
            throw new java.util.NoSuchElementException

        iteratorList(cursor).next
    }
}

class GrizzledSeq[+T](seq: Seq[T]) extends SeqForwarder[T] {

    protected def underlying = seq

    def realSeq = seq

    /**
     * Create a string containing the contents of this sequence, arranged
     * in columns.
     *
     * @param width    total (maximum) columns
     *
     * @return a possibly multiline string containing the columnar output.
     *         The string may have embedded newlines, but it will not end
     *         with a newline.
     */
    def columnarize(width: Int): String = {
        import scala.collection.mutable.ArrayBuffer
        import grizzled.math.util.{max => maxnum}

        val buf = new ArrayBuffer[Char] 

        // Lay them out in columns. Simple-minded for now.
        val strings = seq.map(_.toString).toList
        val colSize = maxnum(strings.map(_.length): _*) + 2
        val colsPerLine = width / colSize
        for ((s, i) <- strings.zipWithIndex) {
            val count = i + 1
            if ((count % colsPerLine) == 0)
                buf += '\n'

            val padding = " " * (colSize - s.length)
            buf ++= (s + padding)
        }

        buf mkString ""
    }

    override def toString = underlying.toString
}

/**
 * Implicit conversions specific to GrizzledSeq.
 */
object GrizzledSeq {

    /**
     * Convert a Scala Seq object to a GrizzledSeq.
     */
    implicit def scalaSeqToGrizzledSeq[T](seq: Seq[T]) = new GrizzledSeq[T](seq)

    /**
     * Convert GrizzledSeq a object to a Scala Seq.
     */
    implicit def grizzledSeqToScalaSeq[T](seq: GrizzledSeq[T]) = seq.realSeq
}

/**
 * Misc. implicit conversions for this package.
 */
object implicits {

    /**
     * Convert a Java collection (from <tt>java.util</tt>) into a
     * <tt>CollectionIterator</tt>.
     */
    implicit def javaCollectionToScalaIterator[T](c: Collection[T]) =
        new CollectionIterator[T](c)
}
