import java.io.*;
import java.util.*;

public class BitStreamWriter
{
	private static final boolean LITTLE_ENDIAN = true;
	private OutputStream out;
	private int buffer;
	private int buflen;

	public BitStreamWriter (OutputStream out)
	{
		this.out = out;
	}

	/** reserve at least 25 bits
	 * */
	private void reserve () throws IOException
	{
		while (buflen >= 8) {
			if (LITTLE_ENDIAN) {
				out.write(buffer);
				buffer >>>= 8;
				buflen -= 8;
			} else {
				buflen -= 8;
				out.write(buffer >>> buflen);
				buffer &= (1 << buflen) - 1;
			}
		}
	}

	/** write remaining buffer.
	 * 0s are padded to byte boundary
	 * */
	public void flush () throws IOException
	{
		reserve();
		if (buflen > 0)
			out.write(buffer);
		buflen = 0;
	}

	/** for debug use only
	 * */
	public void printBuffer ()
	{
		for (int i = buflen - 1; i >= 0; i --)
			System.out.print((buffer >>> i) & 1);
		System.out.println();
	}

	public void writeBit (boolean bit) throws IOException
	{
		writeBit(bit ? 1 : 0);
	}

	public void writeBit (int bit) throws IOException
	{
		assert bit == 0 || bit == 1;
		reserve();
		if (LITTLE_ENDIAN)
			buffer |= bit << buflen;
		else
			buffer = (buffer << 1) | bit;
		buflen ++;
	}

	public void writeZeros (int n) throws IOException
	{
		assert n >= 0;
		while (n > 0) {
			reserve();
			int towrite = Math.min(32 - buflen, n);
			if (LITTLE_ENDIAN) {
			} else {
				buffer <<= towrite;
			}
			buflen += towrite;
			n -= towrite;
		}
	}

	private void writeFixedIntUnchecked (int n, int bits)
	{
		assert bits >= 0 && bits <= 32;
		assert buflen + bits <= 32;
		assert (n & ~ ((1 << bits) - 1)) == 0;
		if (LITTLE_ENDIAN)
			buffer |= n << buflen;
		else
			buffer = (buffer << bits) | n;
		buflen += bits;
	}

	public void writeFixedInt (int n, int bits) throws IOException
	{
		assert bits >= 0 && bits <= 32;
		n &= (1 << bits) - 1;
		reserve();
		if (bits <= 32 - buflen) {
			writeFixedIntUnchecked(n, bits);
		} else {
			if (LITTLE_ENDIAN) {
				writeFixedIntUnchecked(n & 0xff, 8);
				reserve();
				writeFixedIntUnchecked(n >>> 8, bits - 8);
			} else {
				writeFixedIntUnchecked(n >>> 8, bits - 8);
				reserve();
				writeFixedIntUnchecked(n & 0xff, 8);
			}
		}
	}

	/** Elias Gamma coding
	 * @param n  n != 0
	 * */
	public void writeEliasGamma (int n) throws IOException
	{
		assert n != 0;
		int bits = 32 - Integer.numberOfLeadingZeros(n);
		writeZeros(bits - 1);
		writeFixedInt(n, bits);
	}

	/** Exp-Golomb k=0 coding
	 * @param n  n != -1
	 * */
	public void writeExpGolomb0 (int n) throws IOException
	{
		assert n != -1;
		writeEliasGamma(n + 1);
	}

	/** Exp-Golomb k=0 coding
	 * @param n  n != -1
	 * */
	public void writeExpGolomb0 (long n) throws IOException
	{
		assert n != -1;
		if ((n >>> 32) == 0) {
			writeExpGolomb0((int)n);
			return;
		}
		n ++;
		int bits = 64 - Long.numberOfLeadingZeros(n);
		writeZeros(bits - 1);
		writeFixedInt((int)(n >>> 32), bits - 32);
		writeFixedInt((int)n, bits);
	}
}
