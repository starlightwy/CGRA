/* PrintStream.java -- OutputStream for printing output
   Copyright (C) 1998, 1999, 2001, 2003 Free Software Foundation, Inc.

This file is part of GNU Classpath.

GNU Classpath is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 2, or (at your option)
any later version.
 
GNU Classpath is distributed in the hope that it will be useful, but
WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
General Public License for more details.

You should have received a copy of the GNU General Public License
along with GNU Classpath; see the file COPYING.  If not, write to the
Free Software Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA
02111-1307 USA.

Linking this library statically or dynamically with other modules is
making a combined work based on this library.  Thus, the terms and
conditions of the GNU General Public License cover the whole
combination.

As a special exception, the copyright holders of this library give you
permission to link this library with independent modules to produce an
executable, regardless of the license terms of these independent
modules, and to copy and distribute the resulting executable under
terms of your choice, provided that you also meet, for each linked
independent module, the terms and conditions of the license of that
module.  An independent module is a module which is not derived from
or based on this library.  If you modify this library, you may extend
this exception to your version of the library, but you are not
obligated to do so.  If you do not wish to do so, delete this
exception statement from your version. */


package java.io;

/* Written using "Java Class Libraries", 2nd edition, ISBN 0-201-31002-3
 * "The Java Language Specification", ISBN 0-201-63451-1
 * Status:  Believed complete and correct to 1.3
 */

/**
 * This class prints Java primitive values and object to a stream as
 * text.  None of the methods in this class throw an exception.  However,
 * errors can be detected by calling the <code>checkError()</code> method.
 * Additionally, this stream can be designated as "autoflush" when 
 * created so that any writes are automatically flushed to the underlying
 * output sink when the current line is terminated.
 * <p>
 * This class converts char's into byte's using the system default encoding.
 *
 * @author Aaron M. Renn <arenn@urbanophile.com>
 * @author Tom Tromey <tromey@cygnus.com>
 */
public class PrintStream extends FilterOutputStream 
{
  /**
   * This boolean indicates whether or not an error has ever occurred
   * on this stream.
   */
  private boolean error_occurred = false;

  /**
   * This is <code>true</code> if auto-flush is enabled, 
   * <code>false</code> otherwise
   */
  private boolean auto_flush;

  /**
   * The PrintWriter instance this object writes to
   */
  private PrintWriter pw;

  /**
   * Lets us know if the stream is closed
   */
  private boolean closed;

  /**
   * This method intializes a new <code>PrintStream</code> object to write
   * to the specified output sink.
   *
   * @param out The <code>OutputStream</code> to write to.
   */
  public PrintStream (OutputStream out)
  {
    this (out, false);
  }

  /**
   * This method intializes a new <code>PrintStream</code> object to write
   * to the specified output sink.  This constructor also allows "auto-flush"
   * functionality to be specified where the stream will be flushed after
   * every <code>print</code> or <code>println</code> call, when the 
   * <code>write</code> methods with array arguments are called, or when a 
   * single new-line character is written.
   * <p>
   *
   * @param out The <code>OutputStream</code> to write to.
   * @param auto_flush <code>true</code> to flush the stream after every 
   * line, <code>false</code> otherwise
   */
  public PrintStream (OutputStream out, boolean auto_flush)
  {
    super (out);

    pw = new PrintWriter (out, auto_flush);
    this.auto_flush = auto_flush;
  }

  /**
   * This method intializes a new <code>PrintStream</code> object to write
   * to the specified output sink.  This constructor also allows "auto-flush"
   * functionality to be specified where the stream will be flushed after
   * every <code>print</code> or <code>println</code> call, when the 
   * <code>write</code> methods with array arguments are called, or when a 
   * single new-line character is written.
   * <p>
   *
   * @param out The <code>OutputStream</code> to write to.
   * @param auto_flush <code>true</code> to flush the stream after every 
   * line, <code>false</code> otherwise
   * @param encoding The name of the character encoding to use for this
   * object.
   */
  public PrintStream (OutputStream out, boolean auto_flush, String encoding)
    throws UnsupportedEncodingException
  {
    super (out);

    pw = new PrintWriter (new OutputStreamWriter (out, encoding), auto_flush);
    this.auto_flush = auto_flush;
  }

  protected PrintStream () {
	  // Only for NullPrintStream
	  super (null);
  }

/**
   * This method checks to see if an error has occurred on this stream.  Note
   * that once an error has occurred, this method will continue to report
   * <code>true</code> forever for this stream.  Before checking for an
   * error condition, this method flushes the stream.
   *
   * @return <code>true</code> if an error has occurred, 
   * <code>false</code> otherwise
   */
  public boolean checkError ()
  {
    if (!closed)
      flush ();

    return error_occurred | pw.checkError ();
  }

  /**
   * This method can be called by subclasses to indicate that an error
   * has occurred and should be reported by <code>checkError</code>.
   */
  protected void setError ()
  {
    error_occurred = true;
  }

  /**
   * This method closes this stream and all underlying streams.
   */
  public void close ()
  {
    pw.close ();
    closed = true;
  }

  /**
   * This method flushes any buffered bytes to the underlying stream and
   * then flushes that stream as well.
   */
  public void flush ()
  {
    pw.flush();
  }

  /**
   * This methods prints a boolean value to the stream.  <code>true</code>
   * values are printed as "true" and <code>false</code> values are printed
   * as "false".
   *
   * @param b The <code>boolean</code> value to print
   */
  public void print (boolean bool)
  {
    print (String.valueOf (bool));
  }

  /**
   * This method prints an integer to the stream.  The value printed is
   * determined using the <code>String.valueOf()</code> method.
   *
   * @param inum The <code>int</code> value to be printed
   */
  public void print (int inum)
  {
    print (String.valueOf (inum));
  }

  /**
   * This method prints a long to the stream.  The value printed is
   * determined using the <code>String.valueOf()</code> method.
   *
   * @param lnum The <code>long</code> value to be printed
   */
  public void print (long lnum)
  {
    print (String.valueOf (lnum));
  }

  /**
   * This method prints a float to the stream.  The value printed is
   * determined using the <code>String.valueOf()</code> method.
   *
   * @param fnum The <code>float</code> value to be printed
   */
  public void print (float fnum)
  {
    print (String.valueOf (fnum));
  }

  /**
   * This method prints a double to the stream.  The value printed is
   * determined using the <code>String.valueOf()</code> method.
   *
   * @param dnum The <code>double</code> value to be printed
   */
  public void print (double dnum)
  {
    print (String.valueOf (dnum));
  }

  /**
   * This method prints an <code>Object</code> to the stream.  The actual
   * value printed is determined by calling the <code>String.valueOf()</code>
   * method.
   *
   * @param obj The <code>Object</code> to print.
   */
  public void print (Object obj)
  {
    // Don't call pw directly.  Convert to String so we scan for newline
    // characters on auto-flush;
    print (String.valueOf (obj));
  }

  /**
   * This method prints a <code>String</code> to the stream.  The actual
   * value printed depends on the system default encoding.
   *
   * @param str The <code>String</code> to print.
   */
  public void print (String str)
  {
    pw.print (str);

    if (auto_flush)
      flush ();
  }

  /**
   * This method prints a char to the stream.  The actual value printed is
   * determined by the character encoding in use.
   *
   * @param ch The <code>char</code> value to be printed
   */
  public void print (char ch)
  {
    print (String.valueOf (ch));
  }

  /**
   * This method prints an array of characters to the stream.  The actual
   * value printed depends on the system default encoding.
   *
   * @param s The array of characters to print.
   */
  public void print (char[] charArray)
  {
    pw.print (charArray);
  }

  /**
   * This method prints a line separator sequence to the stream.  The value
   * printed is determined by the system property <xmp>line.separator</xmp>
   * and is not necessarily the Unix '\n' newline character.
   */
  public void println ()
  {
    pw.println();
  }

  /**
   * This methods prints a boolean value to the stream.  <code>true</code>
   * values are printed as "true" and <code>false</code> values are printed
   * as "false".
   * <p>
   * This method prints a line termination sequence after printing the value.
   *
   * @param b The <code>boolean</code> value to print
   */
  public void println (boolean bool)
  {
    print (bool);
    println();
  }

  /**
   * This method prints an integer to the stream.  The value printed is
   * determined using the <code>String.valueOf()</code> method.
   * <p>
   * This method prints a line termination sequence after printing the value.
   *
   * @param inum The <code>int</code> value to be printed
   */
  public void println (int inum)
  {
    print (inum);
    println();
  }

  /**
   * This method prints a long to the stream.  The value printed is
   * determined using the <code>String.valueOf()</code> method.
   * <p>
   * This method prints a line termination sequence after printing the value.
   *
   * @param lnum The <code>long</code> value to be printed
   */
  public void println (long lnum)
  {
    print (lnum);
    println();
  }

  /**
   * This method prints a float to the stream.  The value printed is
   * determined using the <code>String.valueOf()</code> method.
   * <p>
   * This method prints a line termination sequence after printing the value.
   *
   * @param fnum The <code>float</code> value to be printed
   */
  public void println (float fnum)
  {
    print (fnum);
    println();
  }

  /**
   * This method prints a double to the stream.  The value printed is
   * determined using the <code>String.valueOf()</code> method.
   * <p>
   * This method prints a line termination sequence after printing the value.
   *
   * @param dnum The <code>double</code> value to be printed
   */
  public void println (double dnum)
  {
    print (dnum);
    println();
  }

  /**
   * This method prints an <code>Object</code> to the stream.  The actual
   * value printed is determined by calling the <code>String.valueOf()</code>
   * method.
   * <p>
   * This method prints a line termination sequence after printing the value.
   *
   * @param obj The <code>Object</code> to print.
   */
  public void println (Object obj)
  {
    print (String.valueOf (obj));
    println();
  }

  /**
   * This method prints a <code>String</code> to the stream.  The actual
   * value printed depends on the system default encoding.
   * <p>
   * This method prints a line termination sequence after printing the value.
   *
   * @param str The <code>String</code> to print.
   */
  public void println (String str)
  {
    print (str);
    println();
  }

  /**
   * This method prints a char to the stream.  The actual value printed is
   * determined by the character encoding in use.
   * <p>
   * This method prints a line termination sequence after printing the value.
   *
   * @param ch The <code>char</code> value to be printed
   */
  public void println (char ch)
  {
    print (ch);
    println();
  }

  /**
   * This method prints an array of characters to the stream.  The actual
   * value printed depends on the system default encoding.
   * <p>
   * This method prints a line termination sequence after printing the value.
   *
   * @param s The array of characters to print.
   */
  public void println (char[] charArray)
  {
    print (charArray);
    println();
  }

  /**
   * This method writes a byte of data to the stream.  If auto-flush is
   * enabled, printing a newline character will cause the stream to be
   * flushed after the character is written.
   * 
   * @param b The byte to be written
   */
  public void write (int oneByte)
  {
    // We actually have to implement this method. Flush first so that
    // things get written in the right order.
    flush();

    try
      {
        out.write (oneByte & 0xff);
        
        if (auto_flush && (oneByte == '\n'))
          flush ();
      }
    catch (IOException e)
      {
        setError ();
      }
  }

  /**
   * This method writes <code>len</code> bytes from the specified array
   * starting at index <code>offset</code> into the array.
   *
   * @param buffer The array of bytes to write
   * @param offset The index into the array to start writing from
   * @param len The number of bytes to write
   */
  public void write (byte[] buffer, int offset, int len)
  {
    // We actually have to implement this method too. Flush first so that
    // things get written in the right order.
    flush();

    try
      {
        out.write (buffer, offset, len);
        
        if (auto_flush)
          flush ();
      }
    catch (IOException e)
      {
        setError ();
      }
  }
} // class PrintStream

