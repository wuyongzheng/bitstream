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

	// read from input until buflen >= bits
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

	public boolean readBit () throws IOException
	{
		reserve(1);
		boolean retval;
		retval = (buffer >>> (-- buflen)) != 0;
		buffer &= (1 << buflen) - 1;
		return retval;
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
	 * @param bits 0 &lt; bits &le; 32
	 * @return unsigned integer n (0 &lt; n &le; 2^bits-1). if bits=32, n can be negative.
	 * */
	public int readFixedInt (int bits) throws IOException
	{
		assert bits > 0 && bits <= 32;
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
	 * @return unsigned log n (1 &lt; n &le; 0xffffffff ffffffff)
	 * */
	public long readEliasGamma () throws IOException
	{
		int bitcount = 0;
		while (!readBit())
			bitcount ++;
		assert bitcount < 64;
		if (bitcount == 0) {
			return 1;
		} else if (bitcount <= 32) {
			return (readFixedInt(bitcount) & 0xffffffffl) | (1l << bitcount);
		} else {
			long retval = 1l << bitcount;
			retval |= (readFixedInt(bitcount - 32) & 0xffffffffl) << 32l;
			retval |= readFixedInt(32) & 0xffffffffl;
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
}
