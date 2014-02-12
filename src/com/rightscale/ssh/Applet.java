package com.rightscale.ssh;

import com.rightscale.ssh.*;
import com.rightscale.util.*;
import com.rightscale.ssh.launchers.*;

import java.lang.reflect.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.net.*;
import java.io.*;
import java.util.*;
import java.security.*;

public class Applet
        extends java.applet.Applet
        implements java.applet.AppletStub, com.rightscale.ssh.UI
{
    public static final String AUTH_METHOD_PUBLIC_KEY = "publickey";
    public static final String AUTH_METHOD_PASSWORD   = "password";
    
    public static final String CHOOSING        = "choosing";
    public static final String USING_NATIVE    = "usingNative";
    public static final String MISSING_KEY     = "missingKey";

    private Launchpad            _launchpad   = new Launchpad(this);
    private boolean              _ranNative   = false;
    private boolean              _hadFailure  = false;
    
    ////
    //// Methods that allow the page's JS to query our state
    ////

    public boolean ranNative() {
        return _ranNative;
    }

    public boolean hadFailure() {
        return _hadFailure;
    }

    ////
    //// AppletStub implementation
    ////

    public void appletResize( int width, int height ){
        resize( width, height );
    }
    
    ////
    //// Applet implementation
    ////

    /**
     * Initialization method that will be called after the applet is loaded
     * into the browser.
     */
    public void init() {
        try {
            AccessController.doPrivileged(new PrivilegedExceptionAction() {
                public Object run() throws IOException {
                    init_();
                    return null;
                }
            });
        }
        catch(PrivilegedActionException e) {
            log("Failed to acquire the privilege necessary to initialize the applet.", e);
        }
    }

    /**
     * Called every time the browser requests a new "instance" of the applet.
     */
    public void start() {
        try {
            AccessController.doPrivileged(new PrivilegedExceptionAction() {
                public Object run() throws IOException {
                    start_();
                    return null;
                }
            });
        }
        catch(PrivilegedActionException e) {
            log("Failed to acquire the privilege necessary to initialize the applet.", e);
        }
    }

    public void stop() {
    }

    ////
    //// Properties and accessors.
    ////

    protected boolean isAutorun() {
        String v = getParameter("autorun");
        if(v == null) {
            return true;
        }

        v = v.toLowerCase();
        if(v.startsWith("y") || v.startsWith("t") || v.startsWith("1")) {
            return true;
        }
        else {
            return false;
        }
    }

    protected boolean isAttemptNative() {
        String v = getParameter("native");
        if(v == null) {
            return false;
        }

        v = v.toLowerCase();
        if(v.startsWith("y") || v.startsWith("t") || v.startsWith("1")) {
            return true;
        }
        else {
            return false;
        }
    }

    protected String getUsername() {
        String v = getParameter("username");

        if(v != null)
            return v;
        else
            return "root";
    }

    protected String getServer() {
        String v = getParameter("server");

        if(v != null)
            return v;
        else
            return "localhost";
    }

    protected String getServerUUID() {
        return getParameter("server-uuid");
    }

    protected String getServerName() {
        String v = getParameter("server-name");

        if(v != null)
            return v;
        else
            return getServer();
    }

    protected String getAuthMethod() {
        if("publickey".equals(getParameter("auth-method")))
            return AUTH_METHOD_PUBLIC_KEY;
        else if("password".equals(getParameter("auth-method")))
            return AUTH_METHOD_PASSWORD;
        else
            return null;
    }

    protected String getServerKeyMaterial() {
        String newline = System.getProperty("line.separator");
        String km = getParameter("openssh-key-material");
        if(km == null) return null;
        return km.replaceAll("\\*", newline);
    }

    protected String getServerPuttyKeyMaterial() {
        String newline = System.getProperty("line.separator");
        String km = getParameter("putty-key-material");
        if(km == null) return null;
        return km.replaceAll("\\*", newline);
    }

    protected String getPassword() {
        return getParameter("password");
    }

    protected String getUserKeyPath() {
        return getParameter("user-key-path");
    }

    protected File getServerKeyFile() {
        return new File(_launchpad.getSafeDirectory(), getServerUUID());
    }

    protected File getServerPuttyKeyFile() {
        return new File(_launchpad.getSafeDirectory(), getServerUUID() + ".ppk");
    }

    protected File getUserKeyFile() {
        String path = getUserKeyPath();
        if(path == null)
            return null;


        //Split the path into elements, accepting either \ or / as a separator
        String[] elements = path.split("/|\\\\");

        String home = System.getProperty("user.home");

        StringBuffer canonPath = new StringBuffer();
        canonPath.append(home);

        for(String elem : elements) {
            canonPath.append(File.separator);
            canonPath.append(elem);
        }

        return new File(canonPath.toString());
    }

    protected File getUserPuttyKeyFile() {
        File f = getUserKeyFile();
        String s = f.getPath();

        if(s.endsWith(".ppk")) {
            return f;
        }
        else {
            return new File(s + ".ppk");
        }
    }

    protected boolean hasUserKeyFile() {
        File f = getUserKeyFile();
        return f.exists();
    }

    protected boolean hasUserPuttyKeyFile() {
        File f = getUserPuttyKeyFile();
        return f.exists();
    }

    protected String getUserKeyMaterial()
    {
        try {
            if(hasUserKeyFile()) {
                File f = getUserKeyFile();
                BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f)));
                StringBuffer sb = new StringBuffer();
                while(br.ready()) {
                    sb.append(br.readLine());
                    sb.append("\n");
                }

                return sb.toString();
            }
            else {
                throw new Error("Key file does not exist");
            }
        }
        catch(Exception e) {
            return null;
        }
    }

    protected String getUserPuttyKeyMaterial()
    {
        try {
            if(hasUserPuttyKeyFile()) {
                File f = getUserPuttyKeyFile();
                BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f)));
                StringBuffer sb = new StringBuffer();
                while(br.ready()) {
                    sb.append(br.readLine());
                    sb.append("\n");
                }

                return sb.toString();
            }
            else {
                throw new Error("Key file does not exist");
            }
        }
        catch(Exception e) {
            return null;
        }
    }

    protected URL getTroubleshootingLink() {
        try {
            return new URL( getParameter("troubleshooting-url") );
        }
        catch(MalformedURLException e) {
            return null;
        }
    }

    ////
    //// "Internal" Applet implementation and related methods; all require the
    //// caller to have already elevated privilege.
    ////

    private void init_()
    {
        _ranNative = _hadFailure = false;
        
        Map keyMaterial = new HashMap();

        if( getAuthMethod().equals(AUTH_METHOD_PUBLIC_KEY) ) {
            if( getUserKeyPath() != null && hasUserKeyFile() ) {
                keyMaterial.put( Launcher.OPENSSH_KEY_FORMAT, getUserKeyMaterial() );
            }
            else if( getServerKeyMaterial() != null ) {
                keyMaterial.put( Launcher.OPENSSH_KEY_FORMAT, getServerKeyMaterial() );
            }
            else {
                boolean why = getUserKeyPath() != null && hasUserKeyFile();
                System.out.println("OpenSSH key material not found (path&file=" + why + ")");
            }

            if( getUserKeyPath() != null && hasUserPuttyKeyFile() ) {
                keyMaterial.put( Launcher.PUTTY_KEY_FORMAT, getUserPuttyKeyMaterial() );
            }
            if( getServerPuttyKeyMaterial() != null ) {
                keyMaterial.put( Launcher.PUTTY_KEY_FORMAT, getServerPuttyKeyMaterial() );
            }
            else {
                boolean why = getUserKeyPath() != null && hasUserPuttyKeyFile();
                System.out.println("PuTTY key material not found (path&file=" + why + ")");
            }

            if(keyMaterial.isEmpty() && getUserKeyPath() == null) {
                log("Unable to find a private key in the applet parameters.", null);
            }
        }

        if(getPassword() != null && getPassword().length() > 0) {
            _launchpad.setPassword(getPassword());
        }

        //Initialize the launchpad business logic
        _launchpad.setUsername(getUsername());
        _launchpad.setServer(getServer());
        _launchpad.setServerUUID(getServerUUID());
        _launchpad.setKeyMaterial(keyMaterial);

        //Fix up the "use native client" button's text for friendlier UI
        if(_launchpad.isNativeClientAvailable()) {
            _actrun.putValue(_actrun.NAME, "Launch " + _launchpad.getNativeClientName());
        }

        //Initialize the UI (only if we haven't already done it)
        if(!_initialized) {
            initUI();
        }

        _initialized = true;
    }

    private void start_()
    {
        try {
            if( isAutorun() ) {
                autorun_();
            }
            else {
                choose_();
            }
        }
        catch(IOException e) {
            log("Encountered an error while invoking the SSH client.", e);
        }
    }

    private boolean autorun_()
            throws IOException
    {
        System.err.println("Attempting autorun...");

        boolean didLaunch = false;

        if( isAttemptNative() ) {
            try {
                setDisplayState(USING_NATIVE);
                _ranNative = didLaunch = _launchpad.run();
            }
            catch(IOException e) {
                didLaunch = false;
                log("Could not invoke your system's SSH client.", e);
            }
        }

        return didLaunch;
    }

    private void choose_()
            throws IOException
    {

        if( getUserKeyPath() != null && !hasUserKeyFile() && !hasUserPuttyKeyFile() ) {
            //We can't find the user's local key file -- just give up!
            _hadFailure = true;
            setDisplayState(MISSING_KEY);
        }
        else if( _launchpad.isNativeClientAvailable() ) {
            setDisplayState(CHOOSING);
        }
    }

    ////
    //// UI fields and functions.
    ////

    boolean        _initialized = false;
    JPanel         _pnlMain     = null;

    Action _actTroubleshoot = new AbstractAction("Troubleshoot") {
        public void actionPerformed(ActionEvent evt) {
            URL url = getTroubleshootingLink();
            if(url != null) {
                Applet.this.getAppletContext().showDocument(url, "_blank");
            }
        }
    };

    Action _actrun = new AbstractAction("Launch SSH") {
        public void actionPerformed(ActionEvent evt) {
            try {
                _ranNative = _launchpad.run();
                setDisplayState(USING_NATIVE);
            }
            catch(IOException e) {
                log("Could not invoke your computer's SSH application.", e);
            }
        }
    };

    private void setDisplayState(String newState) {
        if(_initialized) {
            CardLayout layout = (CardLayout)_pnlMain.getLayout();
            layout.show(_pnlMain, newState);
        }
    }

    private void initUI() {
        //A header that is shared between all display states
        Container header            = createHeaderUI();

        //One panel for each display state the applet can be in
        Container pnlChoosing       = createChoosingUI(),
                  pnlUsingNative    = createUsingNativeUI(),
                  pnlMissingKey     = createMissingKeyUI();

        //Add all of the initialized panels to the main (CardLayout) panel
        _pnlMain = createPanel();
        _pnlMain.setLayout(new CardLayout());
        _pnlMain.add(pnlChoosing, CHOOSING);
        _pnlMain.add(pnlUsingNative, USING_NATIVE);
        _pnlMain.add(pnlMissingKey, MISSING_KEY);

        //Add the main and header panels to ourself
        this.setLayout(new BorderLayout());
        this.add(header, BorderLayout.NORTH);
        this.add(_pnlMain, BorderLayout.CENTER);
    }

    private JPanel createPanel() {
        JPanel pnl = new JPanel();
        pnl.setBackground(Color.white);
        return pnl;
    }

    private Container createHeaderUI() {
        JPanel pnl = createPanel();
        pnl.setLayout(new BoxLayout(pnl, BoxLayout.Y_AXIS));
        JLabel lbl = new JLabel( "Connecting to" );
        lbl.setAlignmentX(JComponent.CENTER_ALIGNMENT);
        pnl.add(lbl);
        lbl = new JLabel( getServerName() );
        lbl.setAlignmentX(JComponent.CENTER_ALIGNMENT);
        pnl.add(lbl);
        pnl.add(Box.createRigidArea(new Dimension(1, 16)));
        return pnl;
    }


    private Container createChoosingUI() {
        JPanel pnl = createPanel();
        Box pnlCenter = Box.createVerticalBox();
        Box pnlButtons = Box.createHorizontalBox();
        JLabel lbl = new JLabel( "How do you want to connect?" );
        lbl.setFont(lbl.getFont().deriveFont(Font.BOLD));
        lbl.setAlignmentX(JComponent.CENTER_ALIGNMENT);
        pnlCenter.add(lbl);
        pnlButtons.add(new JButton(_actrun));
        pnlCenter.add(pnlButtons);
        pnl.setLayout(new BorderLayout());
        pnl.add(pnlCenter, BorderLayout.CENTER);

        return pnl;
    }

    private Container createUsingNativeUI() {
        JPanel pnl = createPanel();
        Box pnlCenter = Box.createVerticalBox();
        Box pnlButtons = Box.createHorizontalBox();
        JLabel lbl = new JLabel( _launchpad.getNativeClientName() + " will launch in a separate window." );
        lbl.setAlignmentX(JComponent.CENTER_ALIGNMENT);
        pnlCenter.add(lbl);
        pnlCenter.add(Box.createRigidArea(new Dimension(1, 16)));
        JButton btnrun = new JButton(_actrun);
                btnrun.setText("New Session");
        pnlButtons.add(btnrun);
        pnlCenter.add(pnlButtons);
        pnl.setLayout(new BorderLayout());
        pnl.add(pnlCenter, BorderLayout.CENTER);
        return pnl;
    }

    private Container createMissingKeyUI() {
        JPanel pnl = createPanel();
        Box pnlCenter = Box.createVerticalBox();

        JLabel lbl = new JLabel("Cannot locate the private key file");
        lbl.setAlignmentX(JComponent.CENTER_ALIGNMENT);
        pnlCenter.add(lbl);

        String path = null;
        try {
            path = getUserKeyFile().getCanonicalPath();
        }
        catch(IOException e) {
            //If we can't even find the canonical path of the file...
            path = getUserKeyPath();
        }
        catch(NullPointerException e) {
            //If no user key file was specified
            path = "(unknown)";
        }

        lbl = new JLabel(path);
        lbl.setAlignmentX(JComponent.CENTER_ALIGNMENT);
        pnlCenter.add(lbl);

        pnlCenter.add(Box.createRigidArea(new Dimension(1, 16)));
        
        lbl = new JLabel("Please change your SSH settings or create the file mentioned above.");
        lbl.setAlignmentX(JComponent.CENTER_ALIGNMENT);
        lbl.setForeground(Color.RED);
        pnlCenter.add(lbl);

        if(getTroubleshootingLink() != null) {
            pnlCenter.add(Box.createRigidArea(new Dimension(1, 16)));
            Box pnlButtons = Box.createHorizontalBox();
            pnlButtons.add(new JButton(_actTroubleshoot));
            pnlCenter.add(pnlButtons);
        }

        pnl.setLayout(new BorderLayout());
        pnl.add(pnlCenter, BorderLayout.CENTER);
        return pnl;
    }
    
    ////
    //// Implementation of com.rightscale.ssh.UI
    ////

    public void log(String message) {
        System.out.println(message);
    }

    public void log(String message, Throwable problem) {
        System.err.println(String.format("%s - %s: %s", message, problem.getClass().getName(), problem.getMessage()));
        problem.printStackTrace();
    }   

    public void alert(String message) {
        log(message);
        JOptionPane.showMessageDialog(null, message, "SSH Launcher", JOptionPane.INFORMATION_MESSAGE);
    }

    public void alert(String message, Throwable problem) {
        log(message, problem);
        JOptionPane.showMessageDialog(null, String.format("%s\n(%s: %s)", message, problem.getClass().getName(), problem.getMessage()), "Error", JOptionPane.ERROR_MESSAGE);        
    }

}
