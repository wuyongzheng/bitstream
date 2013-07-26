import java.io.*;
import java.util.*;

public class BitStreamWriter
{
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
			buflen -= 8;
			out.write(buffer >>> buflen);
			buffer &= (1 << buflen) - 1;
		}
	}

	/** Write the remaining buffer to the underlying OutputStream.
	 * 0s are padded to byte boundary.
	 * The reader has to call sync as well, or create a new reader from this point.
	 * */
	public void sync () throws IOException
	{
		reserve();
		if (buflen > 0) {
			out.write(buffer << (8 - buflen));
			buffer = buflen = 0;
		}
	}

	/** Equivlent to call sync() and close the underlying OutputStream.
	 * */
	public void close () throws IOException
	{
		sync();
		out.close();
		out = null;
	}

	/** for debug use only
	 * */
	public void printBuffer ()
	{
		for (int i = buflen - 1; i >= 0; i --)
			System.out.print((buffer >>> i) & 1);
		System.out.println();
	}

	/** Write a single bit */
	public void writeBit (int bit) throws IOException
	{
		if (bit != 0 && bit != 1)
			throw new IllegalArgumentException("bit is not boolean");
		reserve();
		buffer = (buffer << 1) | bit;
		buflen ++;
	}

	/** Write a single bit */
	public void writeBoolean (boolean bit) throws IOException
	{
		writeBit(bit ? 1 : 0);
	}

	/** Write a <i>bits</i> zero bits
	 * @param bits bits &ge; 0 */
	public void writeZeros (int bits) throws IOException
	{
		if (bits < 0)
			throw new IllegalArgumentException("bits is negative");
		while (bits > 0) {
			reserve();
			int towrite = Math.min(32 - buflen, bits);
			buffer <<= towrite; // it's OK if towrite==32 because buffer is 0 anyway.
			buflen += towrite;
			bits -= towrite;
		}
	}

	/** write 0-based Unary code
	 * @param n n &ge; 0
	 * */
	public void writeUnary (int n) throws IOException
	{
		if (n < 0)
			throw new IllegalArgumentException("n < 0 is not allowed");
		writeZeros(n);
		writeBit(1);
	}

	/** write fixed integer without flushing the buffer */
	private void writeFixedIntUnchecked (int n, int bits)
	{
		assert bits >= 0 && bits <= 31;
		assert buflen + bits <= 32;
		assert (n >>> bits) == 0;
		buffer = (buffer << bits) | n;
		buflen += bits;
	}

	/** Write the least <i>bits</i> bits of integer <i>n</i>.
	 * Big-endian is used.
	 * @param 0 &le; bits &le; 31
	 * */
	public void writeFixedInt (int n, int bits) throws IOException
	{
		//System.out.printf("writeFixedInt(0x%08x, %d)\n", n, bits);
		if (bits < 0 || bits > 31)
			throw new IllegalArgumentException("bits is not in the range of [0,32]. bits=" + bits);
		if (bits == 0)
			return;
		n &= (1 << bits) - 1;
		reserve();
		if (bits <= 32 - buflen) {
			writeFixedIntUnchecked(n, bits);
		} else {
			writeFixedIntUnchecked(n >>> 8, bits - 8);
			reserve();
			writeFixedIntUnchecked(n & 0xff, 8);
		}
	}

	/** Elias Gamma coding.
	 * @param n  1 &le; n &le; Integer.MAX_VALUE
	 * */
	public void writeEliasGamma (int n) throws IOException
	{
		if (n <= 0)
			throw new IllegalArgumentException("n <= 0 is not allowed in Elias Gamma code");
		int bits = 32 - Integer.numberOfLeadingZeros(n);
		writeZeros(bits - 1);
		writeFixedInt(n, bits);
	}

	/** Elias Gamma coding.
	 * @param n  1 &le; n &le; Long.MAX_VALUE
	 * */
	public void writeEliasGamma (long n) throws IOException
	{
		if (n <= 0)
			throw new IllegalArgumentException("n <= 0 is not allowed in Elias Gamma code");
		int bits = 64 - Long.numberOfLeadingZeros(n);
		writeZeros(bits - 1);
		while (bits > 0) {
			reserve();
			int towrite = Math.min(31, Math.min(32 - buflen, bits));
			bits -= towrite;
			writeFixedIntUnchecked((int)(n >>> bits), towrite);
			n &= (1l << bits) - 1;
		}
	}

	/** Exp-Golomb k=0 coding.
	 * @param n  0 &le; n &le; Integer.MAX_VALUE - 1
	 * */
	public void writeExpGolomb0 (int n) throws IOException
	{
		//if (n < 0 || n == Integer.MAX_VALUE - 1)
		//	throw new IllegalArgumentException("n < 0 is not allowed in Exp-Golomb code");
		writeEliasGamma(n + 1);
	}

	/** Exp-Golomb k=0 coding.
	 * @param n  0 &le; n &le; Long.MAX_VALUE - 1
	 * */
	public void writeExpGolomb0 (long n) throws IOException
	{
		//if (n < 0 || n == Long.MAX_VALUE - 1)
		//	throw new IllegalArgumentException("n < 0 is not allowed in Exp-Golomb code");
		writeEliasGamma(n + 1);
	}

	/** Exp-Golomb coding.
	 * @param n  0 &le; n &le; Long.MAX_VALUE - 1
	 * @param k  0 &le; k &le; 31
	 * */
	public void writeExpGolombK (long n, int k) throws IOException
	{
		writeEliasGamma((n >>> k) + 1);
		writeFixedInt((int)n, k);
	}

	static final long [] fibSeries = {
		1l, 2l, 3l, 5l, 8l, 13l, 21l, 34l, 55l, 89l, 144l, 233l, 377l, 610l,
		987l, 1597l, 2584l, 4181l, 6765l, 10946l, 17711l, 28657l, 46368l,
		75025l, 121393l, 196418l, 317811l, 514229l, 832040l, 1346269l,
		2178309l, 3524578l, 5702887l, 9227465l, 14930352l, 24157817l,
		39088169l, 63245986l, 102334155l, 165580141l, 267914296l, 433494437l,
		701408733l, 1134903170l, 1836311903l, 2971215073l, 4807526976l,
		7778742049l, 12586269025l, 20365011074l, 32951280099l, 53316291173l,
		86267571272l, 139583862445l, 225851433717l, 365435296162l,
		591286729879l, 956722026041l, 1548008755920l, 2504730781961l,
		4052739537881l, 6557470319842l, 10610209857723l, 17167680177565l,
		27777890035288l, 44945570212853l, 72723460248141l, 117669030460994l,
		190392490709135l, 308061521170129l, 498454011879264l, 806515533049393l,
		1304969544928657l, 2111485077978050l, 3416454622906707l,
		5527939700884757l, 8944394323791464l, 14472334024676221l,
		23416728348467685l, 37889062373143906l, 61305790721611591l,
		99194853094755497l, 160500643816367088l, 259695496911122585l,
		420196140727489673l, 679891637638612258l, 1100087778366101931l,
		1779979416004714189l, 2880067194370816120l, 4660046610375530309l,
		7540113804746346429l};

	/** Fibonacci code.
	 * @param n 1 &le; n &le; Long.MAX_VALUE
	 * */
	public void writeFibonacci (long n) throws IOException
	{
		if (n <= 0)
			throw new IllegalArgumentException("n <= 0 is not allowed in Fibonacci code");
		int fibn = 0;
		{
			int low = 0, high = fibSeries.length - 1;
			while (low < high) {
				int mid = (low + high + 1) / 2;
				if (n < fibSeries[mid])
					high = mid - 1;
				else
					low = mid;
			}
			fibn = low;
		} // now fibSeries[fibn] is the largest fib number that is <= n.
		//System.out.println("writeFibonacci() fibn=" + fibn);

		/* we cut it into maximum 3 chunks, each 31 bits.
		 * |0... buf0 ...30|31... buf1 ...61|62... buf2 ...92| */
		int buf0 = 0, buf1 = 0, buf2 = 0;
		for (int i = fibn; i >= 62; i --) {
			if (n >= fibSeries[i]) {
				n -= fibSeries[i];
				buf2 |= 1 << (92 - i);
				//System.out.println("set " + i + ":" + fibSeries[i]);
			}
		}
		for (int i = Math.min(fibn, 61); i >= 31; i --) {
			if (n >= fibSeries[i]) {
				n -= fibSeries[i];
				buf1 |= 1 << (61 - i);
				//System.out.println("set " + i + ":" + fibSeries[i]);
			}
		}
		for (int i = Math.min(fibn, 30); i >= 0; i --) {
			if (n >= fibSeries[i]) {
				n -= fibSeries[i];
				buf0 |= 1 << (30 - i);
				//System.out.println("set " + i + ":" + fibSeries[i]);
			}
		}
		assert n == 0;
		writeFixedInt(buf0 >>> (31 - Math.min(31, fibn + 1)), Math.min(31, fibn + 1));
		if (fibn >= 31)
			writeFixedInt(buf1 >>> (31 - Math.min(31, fibn - 30)), Math.min(31, fibn - 30));
		if (fibn >= 62)
			writeFixedInt(buf2 >>> (92 - fibn), fibn - 61);
		writeBit(1);
	}
}
