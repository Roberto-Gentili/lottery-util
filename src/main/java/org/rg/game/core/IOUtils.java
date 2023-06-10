package org.rg.game.core;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;

public class IOUtils {
	public static final IOUtils INSTANCE = new IOUtils();


	public void copy(InputStream input, OutputStream output) {
		try {
			byte[] buffer = new byte[1024];
			int bytesRead = 0;
			while (-1 != (bytesRead = input.read(buffer))) {
				output.write(buffer, 0, bytesRead);
			}
		} catch (IOException e) {
			Throwables.sneakyThrow(e);
		}
	}

	public File writeToNewFile(String absolutePath, String value) {
		try (FileChannel outChan = new FileOutputStream(absolutePath, true).getChannel()) {
		  outChan.truncate(0);
		} catch (IOException exc) {
			//exc.printStackTrace();
		}
		try (FileWriter fileWriter = new FileWriter(absolutePath, false);) {
			fileWriter.write(value);
			fileWriter.flush();
		} catch (IOException exc) {
			Throwables.sneakyThrow(exc);
		}
		return new File(absolutePath);
	}

}
