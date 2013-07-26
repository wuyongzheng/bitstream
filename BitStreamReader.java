import java.io.*;
import java.util.*;

public class BitStreamReader
{
	private InputStream in;
	private int buffer;
	private int buflen;

	public BitStreamReader (InputStream in)
	{
		this.in = in;
	}

	/** read from input until buflen >= bits
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

	/** Discard the current internal buffer.
	 * See BitStreamWriter.sync
	 * */
	public void sync ()
	{
		assert buffer == 0;
		buflen = 0;
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
	 * @return n (1 &le; n &le; Long.MAX_VALUE)
	 * */
	public long readEliasGamma () throws IOException
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

	/**
	 * @return unsigned log n (0 &lt; n &le; 0xffffffff fffffffe)
	 * */
	public long readExpGolomb0 () throws IOException
	{
		return readEliasGamma() - 1;
	}

	/** Exp-Golomb coding.
	 * @param k  0 &le; k &le; 31
	 * */
	public long readExpGolombK (int k) throws IOException
	{
		if (k < 0 || k > 31)
			throw new IllegalArgumentException("invalid k");
		long n = readEliasGamma() - 1;
		return k == 0 ? n : (n << k) | readFixedInt(k);
	}

	/** Fibonacci code.
	 * */
	public long readFibonacci () throws IOException
	{
		long retval = 0;
		int fibn = 0;
		boolean prevbit = false;
		while (true) {
			boolean currbit = readBoolean();
			if (currbit && prevbit)
				break;
			retval += currbit ? BitStreamWriter.fibSeries[fibn] : 0;
			prevbit = currbit;
			fibn ++;
		}
		//System.out.println("readFibonacci() fibn=" + fibn);
		return retval;
	}
}
