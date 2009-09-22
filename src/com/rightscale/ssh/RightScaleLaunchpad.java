/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.rightscale.ssh;

import com.rightscale.ssh.launchers.Launcher;
import com.rightscale.ssh.*;
import com.rightscale.ssh.launchers.*;
import com.rightscale.ssh.launchers.java.*;

import java.lang.reflect.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.io.*;
import java.util.*;
import java.applet.Applet;
import java.applet.AppletStub;
import java.security.*;


/**
 *
 * @author tony
 */
public class RightScaleLaunchpad
    implements Launchpad
{
    static final private int KEY_DELETION_TIMEOUT = 180;

    static final private String[] LAUNCHERS = {
        "com.rightscale.ssh.launchers.osx.Applescript",
        "com.rightscale.ssh.launchers.unix.GnomeTerminal",
        "com.rightscale.ssh.launchers.unix.Konsole",
        "com.rightscale.ssh.launchers.unix.Xterm",
        "com.rightscale.ssh.launchers.windows.PuTTY",
        //"com.rightscale.ssh.launchers.windows.SecureCRT",
        "com.rightscale.ssh.launchers.windows.OpenSSH",
        "com.rightscale.ssh.launchers.windows.GenericSSH"
    };

    private String               _username    = null;
    private String               _server      = null;
    private String               _keyName     = null;
    private Map                  _keyMaterial = null;
    private ArrayList            _launchers   = new ArrayList();
    private Mindterm             _mindterm    = null;
    private Set                  _requiredKeys= new HashSet();
    private Map                  _writtenKeys = new HashMap();

    ////
    //// Launchpad implementation
    ////

    public void init() {
        Class[]  paramTypes = {Launchpad.class};
        Object[] params     = {this};

        //Initialize platform-native launchers
        for(int i = 0; i < LAUNCHERS.length; i++) {
            String cn = LAUNCHERS[i];

            try {
                Constructor ctor = Class.forName(cn).getConstructor(paramTypes);
                Launcher l = (Launcher) ctor.newInstance(params);

                if(hasKeyFormat(l.getRequiredKeyFormat())) {
                    _launchers.add(l);
                    _requiredKeys.add( new Integer(l.getRequiredKeyFormat()) );
                    System.err.println(cn + " is COMPATIBLE.");
                }
                else {
                    System.err.println(cn + " is UNAVAILABLE (missing required key format).");
                }
            }
            catch(Exception e) {
                Throwable t = e;
                while(t.getCause() != null)
                    t = t.getCause();

                System.err.println(cn + " is NOT compatible: " + t);
            }
        }

        //OpenSSH format is always required (for PuTTY)
        _requiredKeys.add(new Integer(Launcher.OPENSSH_KEY_FORMAT));
    }
    
    public File getSafeDirectory() {
        String dir = System.getProperty("user.home");
        dir = dir + "/.rightscale";
        return new File(dir);
    }

    public void reportError(String reason, Exception e) {
        if(e != null) {
            reason = reason + "\n" + e.toString();
        }


        if(e != null) {
            System.err.println(e.toString());
            e.printStackTrace(System.err);
        }

        report(JOptionPane.ERROR_MESSAGE, "Error", reason);
    }

    ////
    //// Additional UI convenience methods
    ////

    public void reportInfo(String message) {
        report(JOptionPane.INFORMATION_MESSAGE, "SSH Launcher", message);
    }

    public void report(int icon, String title, String message) {
        JOptionPane.showMessageDialog(null, message, title, icon);
    }

    ////
    //// Read/write properties
    ////

    public String getKeyMaterial(int format) {
        Object o = _keyMaterial.get(new Integer(format));
        if(o != null) {
            return (String)o;
        }
        else {
            return null;
        }
    }

    public void setKeyMaterial(Map keyMaterial) {
        _keyMaterial = keyMaterial;
    }

    public String getUsername() {
        return _username;
    }

    public void setUsername(String username) {
        _username = username;
    }

    public String getServer() {
        return _server;
    }

    public void setServer(String server) {
        _server = server;
    }
    
    public String getKeyName() {
        return _keyName;
    }

    public void setKeyName(String keyName) {
        _keyName = keyName;
    }


    ////
    //// Read-only and derived properties
    ////

    public boolean hasKeyFormat(int keyFormat) {
        return ( _keyMaterial.get(new Integer(keyFormat)) != null );
    }

    public boolean isNativeClientAvailable() {
        return (_launchers.size() > 0);
    }

    public String getNativeClientName() {
        if(_launchers.size() > 0) {
            return ((Launcher)_launchers.get(0)).getFriendlyName();
        }
        else {
            return "MindTerm";
        }
    }

    public File getKeyFile(int keyFormat) {
        switch(keyFormat) {
            case Launcher.OPENSSH_KEY_FORMAT:
                return new File(getSafeDirectory(), getKeyName());
            case Launcher.PUTTY_KEY_FORMAT:
                return new File(getSafeDirectory(), getKeyName() + ".ppk");
            default:
                throw new Error("Unsupported key format");
        }

    }

    ////
    //// Methods that do work
    ////

    public void writePrivateKeys()
            throws IOException
    {
        if(_requiredKeys.contains(new Integer(Launcher.OPENSSH_KEY_FORMAT))) {
            String matl = getKeyMaterial(Launcher.OPENSSH_KEY_FORMAT);
            File   key  = getKeyFile(Launcher.OPENSSH_KEY_FORMAT);
            SimpleLauncher.writePrivateKey(matl, key, KEY_DELETION_TIMEOUT);

        }
        if(_requiredKeys.contains(new Integer(Launcher.PUTTY_KEY_FORMAT))) {
            String matl = getKeyMaterial(Launcher.PUTTY_KEY_FORMAT);
            File   key  = getKeyFile(Launcher.PUTTY_KEY_FORMAT);
            SimpleLauncher.writePrivateKey(matl, key, KEY_DELETION_TIMEOUT);
        }
    }

    public boolean runMindterm(Applet applet, AppletStub stub)
            throws IOException
    {
        try {
            writePrivateKeys();
        }
        catch(IOException e) {
            reportError("Could not write your private key file.", e);
            return false;
        }

        _mindterm = new Mindterm(this, applet, stub);
        _mindterm.run( getUsername(), getServer(), getKeyFile(Launcher.OPENSSH_KEY_FORMAT) );
        return true;
    }

    public boolean runNative()
            throws IOException
    {
        try {
            writePrivateKeys();
        }
        catch(IOException e) {
            reportError("Could not write your private key file.", e);
            return false;
        }

        //Try all the launchers in sequence
        Iterator it = _launchers.iterator();
        while( it.hasNext() ) {
            Launcher l = (Launcher)it.next();

            System.err.println("  Running " + l.getClass().getName());

            try {
                File keyFile = getKeyFile(l.getRequiredKeyFormat());
                l.run( getUsername(), getServer(), keyFile );
                return true;
            }
            catch(IOException e) {
                System.err.println("Failed to launch using " + l.getFriendlyName() + ":");
                System.err.println(e.toString());
            }
        }

        return false;
    }
}
