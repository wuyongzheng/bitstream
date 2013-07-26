import java.io.*;

public class Test {
	private static void testEmpty () throws Exception
	{
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		BitOutputStream writer = new BitOutputStream(out);
		writer.close();
		assert out.size() == 0;

		ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
		BitInputStream reader = new BitInputStream(in);
	}

	private static void testAll () throws Exception
	{
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		BitOutputStream writer = new BitOutputStream(out);
		writer.writeBoolean(true);
		writer.writeZeros(2);
		writer.writeBoolean(false);
		writer.writeUnary(0);
		writer.writeUnary(5);
		writer.writeFixedInt(100, 7);
		writer.writeFixedInt(100000, 17);
		writer.writeFixedInt(100000, 20);
		writer.writeEliasGamma(1);
		writer.writeEliasGamma(1000);
		writer.writeEliasGamma(Integer.MAX_VALUE);
		writer.writeEliasGamma(Integer.MAX_VALUE - 1000);
		writer.writeEliasGamma(Long.MAX_VALUE);
		writer.writeEliasGamma(Long.MAX_VALUE - 1000);
		writer.writeExpGolomb0(3);
		writer.writeExpGolomb0(3000);
		writer.writeFibonacci(3);
		writer.writeFibonacci(3000);
		writer.writeFibonacci(300000000);
		writer.writeFibonacci(3000000000000l);
		writer.writeFibonacci(Integer.MAX_VALUE);
		writer.writeFibonacci(Long.MAX_VALUE);
		writer.writeFibonacci(Long.MAX_VALUE - 1000);
		writer.close();

		{
			byte [] arr = out.toByteArray();
			for (int i = 0; i < arr.length; i ++) {
				for (int j = 0; j < 8; j ++)
					System.out.print((arr[i] >>> (7 - j)) & 1);
				System.out.println();
			}
		}

		ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
		BitInputStream reader = new BitInputStream(in);
		assert reader.readBoolean() == true;
		assert reader.readBoolean() == false;
		assert reader.readBoolean() == false;
		assert reader.readBoolean() == false;
		assert reader.readUnary() == 0;
		assert reader.readUnary() == 5;
		assert reader.readFixedInt(7) == 100;
		assert reader.readFixedInt(17) == 100000;
		assert reader.readFixedInt(20) == 100000;
		assert reader.readEliasGamma() == 1;
		assert reader.readEliasGamma() == 1000;
		assert reader.readEliasGamma() == Integer.MAX_VALUE;
		assert reader.readEliasGamma() == Integer.MAX_VALUE - 1000;
		assert reader.readEliasGammaLong() == Long.MAX_VALUE;
		assert reader.readEliasGammaLong() == Long.MAX_VALUE - 1000;
		assert reader.readExpGolomb0() == 3;
		assert reader.readExpGolomb0() == 3000;
		assert reader.readFibonacci() == 3;
		assert reader.readFibonacci() == 3000;
		assert reader.readFibonacci() == 300000000;
		assert reader.readFibonacci() == 3000000000000l;
		assert reader.readFibonacci() == Integer.MAX_VALUE;
		assert reader.readFibonacci() == Long.MAX_VALUE;
		assert reader.readFibonacci() == Long.MAX_VALUE - 1000;
	}

	public static void main (String [] args) throws Exception
	{
		testEmpty();
		testAll();
	}
}
