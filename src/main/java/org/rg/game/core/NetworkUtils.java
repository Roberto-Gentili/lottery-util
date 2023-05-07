package org.rg.game.core;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class NetworkUtils {
	public static final NetworkUtils INSTANCE = new NetworkUtils();


	public String thisHostName() {
		try {
			return InetAddress.getLocalHost().getHostName();
		} catch (UnknownHostException exc) {
			return Throwables.sneakyThrow(exc);
		}
	}

}
