import java.io.InterruptedIOException;

import javax.microedition.io.*;
import javax.microedition.lcdui.*;
import javax.microedition.midlet.MIDlet;

public class ServerApp extends MIDlet implements Runnable, CommandListener {
	
	static final Font smallfont = Font.getFont(0, 0, Font.SIZE_SMALL);

	static String server = "Something/1.0";
	static ServerApp inst;
	
	static Form form;
	private TextField portField;
	private TextField rootField;
	private Command exitCmd;
	private Command startCmd;
	private Command openCmd;
	private Command stopCmd;
	
	int port = 12345;
	static String root;
	
	private boolean started;
	private int clients;

	private ServerSocketConnection socket;
	private Thread thread;

	protected void destroyApp(boolean u) {
	}

	protected void pauseApp() {
	}

	protected void startApp() {
		if (inst != null) return;
		inst = this;
		form = new Form("Http server");
		form.addCommand(exitCmd = new Command("Exit", Command.EXIT, 1));
		form.addCommand(startCmd = new Command("Start", Command.SCREEN, 1));
		openCmd = new Command("Open in browser", Command.SCREEN, 2);
		stopCmd = new Command("Stop", Command.SCREEN, 3);
		form.append(portField = new TextField("Port", "12345", 6, TextField.NUMERIC));
		String root = System.getProperty("fileconn.dir.photos");
		if (root == null) root = "file:///C:/";
		form.append(rootField = new TextField("Root", root, 128, TextField.NON_PREDICTIVE));
		form.setCommandListener(this);
		Display.getDisplay(this).setCurrent(form);
	}

	public void commandAction(Command c, Displayable d) {
		if (c == startCmd) { // start server
			if (started) return;
			form.removeCommand(startCmd);
			form.addCommand(stopCmd);
			(thread = new Thread(this, "HttpServer")).start();
			return;
		}
		if (c == openCmd) { // open in browser
			if (!started) {
				log("Server not open!");
				return;
			}
			try {
				if (platformRequest("http://127.0.0.1:" + port)) {
					// if device needs to close app then it's not supported
					log("Exit requested!");
				}
			} catch (Exception e) {
				log("Open error: " + e.toString());
				e.printStackTrace();
			}
			return;
		}
		if (c == stopCmd) {
			if (!started || thread == null) return;
			form.removeCommand(stopCmd);
			log("Stopping server");
			if (socket != null) {
				try {
					socket.close();
				} catch (Exception e) {}
			}
			thread.interrupt();
			thread = null;
		}
		if (c == exitCmd) {
			notifyDestroyed();
			return;
		}
	}

	public void run() {
		try {
			port = Integer.parseInt(portField.getString());
			root = rootField.getString();
			log("Port: " + port);
			socket = (ServerSocketConnection) Connector.open("socket://:" + port);
			started = true;
			log("Server open: " + socket);
			log("Local address: " + socket.getLocalAddress() + " (" + System.getProperty("microedition.hostname") + ')');
			form.addCommand(openCmd);
			try {
				while (thread != null) {
					new Thread(new Client(socket.acceptAndOpen()), "HttpClient-" + (++clients)).start();
				}
			} finally {
				socket.close();
			}
		} catch (Throwable e) {
			if (!(e instanceof InterruptedException || e instanceof InterruptedIOException)) {
				log("Error: " + e.toString());
			}
			e.printStackTrace();
		}
		log("Server closed");
		started = false;
		form.addCommand(startCmd);
	}

	static void log(String s) {
		StringItem t = new StringItem(null, s.concat("\n"));
		t.setFont(smallfont);
		form.append(t);
		System.out.println(s);
	}

}
