import java.io.*;
import java.util.Enumeration;
import java.util.Vector;

import javax.microedition.io.Connector;
import javax.microedition.io.SocketConnection;
import javax.microedition.io.StreamConnection;
import javax.microedition.io.file.FileConnection;

public class Client implements Runnable {

	private static final byte[] CRLF = "\r\n".getBytes();
	private static final String HTML_HEADER = "Content-Type: text/html; charset=utf8";

	private StreamConnection s;
	private OutputStream out;
	private boolean responsed;

	public Client(StreamConnection connection) {
		this.s = connection;
	}

	public void run() {
		try {
	    	String client;
	    	if (s instanceof SocketConnection)
	    		client = (((SocketConnection) s).getAddress() + ":" + ((SocketConnection) s).getPort());
	    	else client = s.toString();
			ServerApp.log("Client connected: " + client);
			
			InputStream in = s.openDataInputStream();
			OutputStream out = this.out = s.openDataOutputStream();
	
			// read request headers
			Vector headers = new Vector();
			StringBuffer sb = new StringBuffer();
			try {
				for (int c, l = 0; (c = in.read()) != -1;) {
					if (c == '\n') {
//						sb.append((char) c);
						headers.addElement(sb.toString());
						sb.setLength(0);
						if (++l == 2) break;
						continue;
					}
					if (c != '\r') {
						l = 0;
						sb.append((char) c);
					}
				}
				
				// handle request
				boolean handled = false;
				try {
					res: {
						String req;
						if (headers.size() == 0 ||
								(req = (String) headers.elementAt(0)).length() == 0) {
							break res;
						}
						int i = req.indexOf(' ');
						if (i == -1 || req.indexOf(' ', i + 1) == -1)
							break res;
						
						String method = req.substring(0, i),
								path = req.substring(i + 1, req.indexOf(' ', i + 1)),
								protocol = req.substring(req.indexOf(' ', i + 1) + 1);
						if (!protocol.startsWith("HTTP/1.") ||
								!path.startsWith("/")) {
							break res;
						}
						
						ServerApp.log("Request: " + req);
						
						if (!"GET".equals(method)) {
							handled = true;
							beginResponse(501, "Not Implemented");
							out.write(CRLF);
							break res;
						}
						
						if (path.length() == 1) { // index
							handled = true;
							InputStream rin = getClass().getResourceAsStream("/index.html");
							if (rin == null) {
								beginResponse(404, "Not Found");
								out.write(CRLF);
								break res;
							}
							
							beginResponse(200, "OK");
							writeHeader(HTML_HEADER);
							writeBody(rin);
							break res;
						}
						
						if ("/favicon.ico".equals(path)) {
							handled = true;
							InputStream rin = getClass().getResourceAsStream("/favicon.ico");
							if (rin == null) {
								beginResponse(404, "Not Found");
								out.write(CRLF);
								break res;
							}
							
							beginResponse(200, "OK");
							writeHeader("Content-Type: image/vnd.microsoft.icon");
							writeBody(rin);
							break res;
						}
						
						// query
//						String query = null;
						if ((i = path.indexOf('?')) != -1) {
//							query = path.substring(i + 1);
							path = path.substring(0, i);
						}
						
						if (path.startsWith("/res")) {
							handled = true;
							InputStream rin = getClass().getResourceAsStream(path = decodeURL(path.substring(4)));
							if (rin == null) {
								beginResponse(404, "Not Found");
								out.write(CRLF);
								break res;
							}

							beginResponse(200, "OK");
							writeTypeByFile(path);
							writeBody(rin);
							break res;
						}
						
//						path = decodeURL(path);
						FileConnection fc;
						try {
							Thread.sleep(100);
							fc = (FileConnection) Connector.open(ServerApp.root.concat(path.substring(1)), Connector.READ);
						} catch (Exception e) {
							handled = true;
							beginResponse(500, "Internal Server Error");
							writeBody(e.toString());
							break res;
						}
						try {
							if (!fc.canRead()) {
								handled = true;
								beginResponse(403, "Forbidden");
								out.write(CRLF);
								break res;
							}
							
							if (!fc.exists()) {
								handled = true;
								beginResponse(404, "Not Found");
								out.write(CRLF);
								break res;
							}
							
							if (fc.isDirectory()) {
								handled = true;
								sb.append("<html><head><title>")
								.append("Index of ").append(path)
								.append("</title></head><body><h1>")
								.append("Index of ").append(path)
								.append("</h1><table><tr><th>Name</th><th>Last modified</th><th>Size</th></tr>")
								.append("<tr><th colspan=\"5\"><hr></th></tr>")
								;
								
								String r = path.substring(1);
								if (r.endsWith("/")) r = r.substring(0, r.length() - 1);
								
								if ((i = r.indexOf('/')) != -1 || r.length() != 0) {
									String parent = "/";
									if (r.indexOf('/', i + 1) != -1) {
										parent = "/".concat(r.substring(0, r.lastIndexOf('/')));
									}
									sb.append("<tr><td><a href=\"")
									.append(parent)
									.append("\">Parent Directory</a></td><td></td><td align=\"right\"></td></tr>")
									;
								}
								
//								boolean b = false;
								for(Enumeration list = fc.list(); list.hasMoreElements(); ) {
									String s = (String) list.nextElement();
									sb.append("<tr><td>");
									if(s.endsWith("/")) {
										sb.append("<a href=\"")
										.append(encodeURL(s.substring(0, s.length() - 1)))
										.append("/\">")
										.append(s).append("</a></td>")
										
										.append("<td></td>")
										
										.append("<td align=\"right\">-</td>")
										;
									} else {
//										if (b) fc.setFileConnection("..");
//										else b = true;
//										fc.setFileConnection(s);
										sb.append("<a href=\"").append(encodeURL(s)).append("\">")
										.append(s).append("</a></td>")

										.append("<td></td>")

										.append("<td align=\"right\"></td>")
//										.append("<td align=\"right\">").append(fc.fileSize()).append("</td>")
										;
									}
									sb.append("</tr>");
								}
								
								sb.append("</table></body></html>");

								beginResponse(200, "OK");
								writeHeader(HTML_HEADER);
								writeBody(sb);
								break res;
							}
							
							InputStream fin = fc.openDataInputStream();
							handled = true;
							try {
								beginResponse(200, "OK");
								writeHeader("Content-Length: ".concat(Long.toString(fc.fileSize())));
								writeTypeByFile(fc.getName());
								writeBody(fin);
							} finally {
								fin.close();
							}
						} finally {
							fc.close();
						}
					}
				} catch (Exception e) {
					ServerApp.log("Client error: " + e.toString());
					e.printStackTrace();
					if (!responsed) {
						beginResponse(500, "Internal Server Error");
						writeBody(e.toString());
					}
				}
				if (!handled && !responsed) {
					beginResponse(400, "Bad Request");
					out.write(CRLF);
				}
				out.flush();
			} finally {
				in.close();
				out.close();
			}
		} catch (Throwable e) {
			ServerApp.log("Client error: " + e.toString());
			e.printStackTrace();
		} finally {
			try {
				s.close();
			} catch (Exception ignored) {}
		}
	}

	private void beginResponse(int code, String message) throws IOException {
		if (responsed) throw new IllegalStateException("beginResponse()");
		ServerApp.log("Response: " + code + " " + message);
		responsed = true;
		out.write("HTTP/1.1 ".getBytes());
		out.write(Integer.toString(code).getBytes());
		out.write(' ');
		if (message != null) out.write(message.getBytes());
		out.write(CRLF);
		if (ServerApp.server != null)
			writeHeader("Server: ".concat(ServerApp.server));
		writeHeader("Connection: close");
	}

	private void writeHeader(String s) throws IOException {
		System.out.println("header written: " + s);
		out.write(s.getBytes());
		out.write(CRLF);
	}
	
	private void writeBody(String s) throws IOException {
		out.write(CRLF);
		out.write(s.getBytes("UTF-8"));
	}
	
	private void writeBody(StringBuffer s) throws IOException {
		out.write(CRLF);
		out.write(s.toString().getBytes("UTF-8"));
	}
	
	private void writeBody(InputStream in) throws IOException {
		out.write(CRLF);
		byte[] b = new byte[16384];
		int i;
		while ((i = in.read(b)) != -1) {
			out.write(b, 0, i);
			out.flush();
		}
	}
	
	private void writeTypeByFile(String n) throws IOException {
		// TODO mime-types table
		String t = null;
		t: {
			if (n.endsWith(".html")) {
				t = "text/html";
				break t;
			}
			if (n.endsWith(".jpg") ||
					n.endsWith(".jpeg")) {
				t = "image/jpeg";
				break t;
			}
			if (n.endsWith(".png")) {
				t = "image/png";
				break t;
			}
			if (n.endsWith(".txt")) {
				t = "text/plain";
				break t;
			}
			if (n.endsWith(".jar")) {
				t = "application/java-archive";
				break t;
			}
			if (n.endsWith(".zip")) {
				t = "application/zip";
				break t;
			}
			if (n.endsWith(".mp4")) {
				t = "video/mp4";
				break t;
			}
			if (n.endsWith(".mp3")) {
				t = "audio/mpeg";
				break t;
			}
		}
		if (t != null)
			writeHeader("Content-Type: ".concat(t));
	}
	
	static String encodeURL(String url) {
		StringBuffer sb = new StringBuffer();
		char[] chars = url.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			int c = chars[i];
			if (65 <= c && c <= 90) {
				sb.append((char) c);
			} else if (97 <= c && c <= 122) {
				sb.append((char) c);
			} else if (48 <= c && c <= 57) {
				sb.append((char) c);
			} else if (c == 32) {
				sb.append("%20");
			} else if (c == 45 || c == 95 || c == 46 || c == 33 || c == 126 || c == 42 || c == 39 || c == 40
					|| c == 41) {
				sb.append((char) c);
			} else if (c <= 127) {
				sb.append(hex(c));
			} else if (c <= 2047) {
				sb.append(hex(0xC0 | c >> 6));
				sb.append(hex(0x80 | c & 0x3F));
			} else {
				sb.append(hex(0xE0 | c >> 12));
				sb.append(hex(0x80 | c >> 6 & 0x3F));
				sb.append(hex(0x80 | c & 0x3F));
			}
		}
		return sb.toString();
	}

	private static String hex(int i) {
		String s = Integer.toHexString(i);
		return "%".concat(s.length() < 2 ? "0" : "").concat(s);
	}
	
	static String decodeURL(String s) {
		if(s == null) {
			return null;
		}
		boolean needToChange = false;
		int numChars = s.length();
		StringBuffer sb = new StringBuffer(numChars > 500 ? numChars / 2 : numChars);
		int i = 0;
		char c;
		byte[] bytes = null;
		try {
			while (i < numChars) {
				c = s.charAt(i);
				switch (c) {
				case '%':
					if (bytes == null)
						bytes = new byte[(numChars - i) / 3];
					int pos = 0;
					while (((i + 2) < numChars) && (c == '%')) {
						int v = Integer.parseInt(s.substring(i + 1, i + 3), 16);
						if (v < 0)
							throw new IllegalArgumentException();
						bytes[pos++] = (byte) v;
						i += 3;
						if (i < numChars)
							c = s.charAt(i);
					}
					if ((i < numChars) && (c == '%'))
						throw new IllegalArgumentException();
					sb.append(new String(bytes, 0, pos, "UTF-8"));
					needToChange = true;
					break;
				default:
					sb.append(c);
					i++;
					break;
				}
			}
		} catch (IOException e) {
			throw new RuntimeException(e.toString());
		}
		return (needToChange ? sb.toString() : s);
	}

}
