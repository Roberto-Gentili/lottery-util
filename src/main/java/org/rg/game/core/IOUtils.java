package org.rg.game.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

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

}
