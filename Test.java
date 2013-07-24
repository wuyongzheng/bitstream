import java.io.*;

public class Test {
	private static void testEmpty () throws Exception
	{
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		BitStreamWriter writer = new BitStreamWriter(out);
		writer.flush();
		assert out.size() == 0;

		ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
		BitStreamReader reader = new BitStreamReader(in);
	}

	private static void testAll () throws Exception
	{
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		BitStreamWriter writer = new BitStreamWriter(out);
		writer.writeBit(true);
		writer.writeZeros(2);
		writer.writeBit(false);
		writer.writeFixedInt(100, 7);
		writer.writeFixedInt(100000, 17);
		writer.writeFixedInt(100000, 20);
		writer.writeEliasGamma(1);
		writer.writeEliasGamma(1000);
		writer.writeExpGolomb0(3);
		writer.writeExpGolomb0(3000);
		writer.flush();

		{
			byte [] arr = out.toByteArray();
			for (int i = 0; i < arr.length; i ++) {
				for (int j = 0; j < 8; j ++)
					System.out.print((arr[i] >>> (7 - j)) & 1);
				System.out.println();
			}
		}

		ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
		BitStreamReader reader = new BitStreamReader(in);
		assert reader.readBit() == true;
		assert reader.readBit() == false;
		assert reader.readBit() == false;
		assert reader.readBit() == false;
		assert reader.readFixedInt(7) == 100;
		assert reader.readFixedInt(17) == 100000;
		assert reader.readFixedInt(20) == 100000;
		assert reader.readEliasGamma() == 1;
		assert reader.readEliasGamma() == 1000;
		assert reader.readExpGolomb0() == 3;
		assert reader.readExpGolomb0() == 3000;
	}

	public static void main (String [] args) throws Exception
	{
		testEmpty();
		testAll();
	}
}
