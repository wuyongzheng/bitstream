import java.io.*;
import java.util.*;

/** BitInputStream reads bits from an underlying OutputStream.
 * See BitOutputStream for sample usage.
 * */
public class BitInputStream extends InputStream
{
	private InputStream in;
	private int buffer;
	private int buflen;
	private int markBuffer;
	private int markBuflen;

	public BitInputStream (InputStream in)
	{
		this.in = in;
	}

	/** read from input until buflen &ge; bits
	 * */
	private void reserve (int bits) throws IOException
	{
		assert bits <= 25;
		while (buflen < bits) {
			int n = in.read();
			if (n == -1)
				throw new EOFException();
			buffer = (buffer << 8) | n;
			buflen += 8;
		}
	}

	/** Get the number of bits in the internal buffer.
	 * The returned value is guaranteed to be within [0,7].
	 * The returned value is always 0 if it is called immidiately after calling sync().
	 * */
	public int getBufferSize ()
	{
		return buflen;
	}

	/** Discard the current internal buffer.
	 * See BitOutputStream.sync
	 * */
	public void sync ()
	{
		assert buffer == 0;
		buflen = 0;
	}

	/** Call sync() and then call read() of the underlying InputStream */
	public int read () throws IOException
	{
		sync();
		return in.read();
	}

	/** Call sync() and then call read() of the underlying InputStream */
	public int read (byte[] b) throws IOException
	{
		sync();
		return in.read(b);
	}

	/** Call sync() and then call read() of the underlying InputStream */
	public int read (byte[] b, int off, int len) throws IOException
	{
		sync();
		return in.read(b, off, len);
	}

	/** Call sync() and then call skip() of the underlying InputStream */
	public long skip (long n) throws IOException
	{
		sync();
		return in.skip(n);
	}

	public int available () throws IOException
	{
		return in.available();
	}

	public void mark (int readlimit)
	{
		markBuffer = buffer;
		markBuflen = buflen;
		in.mark(readlimit);
	}

	public void reset () throws IOException
	{
		buffer = markBuffer;
		buflen = markBuflen;
		in.reset();
	}

	public boolean markSupported ()
	{
		return in.markSupported();
	}

	/** Close the underlying InputStream
	 * */
	public void close () throws IOException
	{
		in.close();
		in = null;
	}

	/** Read a single bit.
	 * @return 0 or 1
	 * */
	public int readBit () throws IOException
	{
		reserve(1);
		int retval;
		retval = buffer >>> (-- buflen);
		buffer &= (1 << buflen) - 1;
		return retval;
	}

	/** A convenient wrapper of readBit()
	 * @return true is the bit is 1, false otherwise
	 * */
	public boolean readBoolean () throws IOException
	{
		return readBit() != 0;
	}

	/** read 0-based Unary code
	 * */
	public int readUnary () throws IOException
	{
		int bitcount = 0;
		while (!readBoolean())
			bitcount ++;
		return bitcount;
	}

	private int readFixedIntUnchecked (int bits)
	{
		assert bits > 0 && buflen >= bits;
		int retval;
		retval = buffer >>> (buflen - bits);
		buflen -= bits;
		buffer &= (1 << buflen) - 1;
		return retval;
	}

	/**
	 * @param bits 1 &le; bits &le; 31
	 * @return positive integer n (0 &lt; n &le; 2^bits - 1).
	 * */
	public int readFixedInt (int bits) throws IOException
	{
		if (bits < 1 || bits > 31)
			throw new IllegalArgumentException("bits is not in the range of [1,31]. bits=" + bits);
		reserve(Math.min(25, bits));
		if (buflen >= bits) {
			return readFixedIntUnchecked(bits);
		} else {
			int retval;
			retval = readFixedIntUnchecked(bits - 8) << 8;
			reserve(8);
			retval |= readFixedIntUnchecked(8);
			return retval;
		}
	}

	/**
	 * @return n (1 &le; n &le; Integer.MAX_VALUE)
	 * */
	public int readEliasGamma () throws IOException
	{
		int bitcount = 0;
		while (!readBoolean())
			bitcount ++;
		if (bitcount > 30)
			throw new IllegalArgumentException("number too big to fit in int type"); //TODO: is there a better fit exception?
		if (bitcount == 0) {
			return 1;
		} else {
			return readFixedInt(bitcount) | (1 << bitcount);
		}
	}

	/**
	 * @return n (1 &le; n &le; Long.MAX_VALUE)
	 * */
	public long readEliasGammaLong () throws IOException
	{
		int bitcount = 0;
		while (!readBoolean())
			bitcount ++;
		if (bitcount > 62)
			throw new IllegalArgumentException("number too big to fit in long type"); //TODO: is there a better fit exception?
		if (bitcount == 0) {
			return 1;
		} else if (bitcount <= 31) {
			return readFixedInt(bitcount) | (1l << bitcount);
		} else {
			long retval = 1l << bitcount;
			retval |= (long)readFixedInt(bitcount - 31) << 31;
			retval |= readFixedInt(31);
			return retval;
		}
	}

	/** An variation of Elias Gamma coding which allows negative values.
	 * EliasGammaAlt(0)  = EliasGamma(1)
	 * EliasGammaAlt(1)  = EliasGamma(2)
	 * EliasGammaAlt(-1) = EliasGamma(3)
	 * EliasGammaAlt(2)  = EliasGamma(4)
	 * EliasGammaAlt(-2) = EliasGamma(5)
	 * */
	public int readEliasGammaAlt () throws IOException
	{
		int n = readEliasGamma();
		if (n == 1)
			return 0;
		return n % 2 == 0 ? n/2 : -(n/2);
	}

	/**
	 * @return n (0 &lt; n &le; Integer.MAX_VALUE - 1)
	 * */
	public int readExpGolomb0 () throws IOException
	{
		return readEliasGamma() - 1;
	}

	/**
	 * @return n (0 &lt; n &le; Long.MAX_VALUE - 1)
	 * */
	public long readExpGolomb0Long () throws IOException
	{
		return readEliasGammaLong() - 1;
	}

	/** Exp-Golomb coding.
	 * @param k  0 &le; k &le; 31
	 * */
	public long readExpGolombK (int k) throws IOException
	{
		if (k < 0 || k > 31)
			throw new IllegalArgumentException("invalid k");
		long n = readEliasGammaLong() - 1;
		return k == 0 ? n : (n << k) | readFixedInt(k);
	}

	/** Fibonacci code.
	 * */
	public int readFibonacci () throws IOException
	{
		int retval = 0;
		int fibn = 0;
		boolean prevbit = false;
		while (true) {
			boolean currbit = readBoolean();
			if (currbit && prevbit)
				break;
			retval += currbit ? BitOutputStream.fibSeries[fibn] : 0;
			prevbit = currbit;
			fibn ++;
		}
		return retval;
	}

	/** Fibonacci code.
	 * */
	public long readFibonacciLong () throws IOException
	{
		long retval = 0;
		int fibn = 0;
		boolean prevbit = false;
		while (true) {
			boolean currbit = readBoolean();
			if (currbit && prevbit)
				break;
			retval += currbit ? BitOutputStream.fibSeriesLong[fibn] : 0;
			prevbit = currbit;
			fibn ++;
		}
		return retval;
	}
}
