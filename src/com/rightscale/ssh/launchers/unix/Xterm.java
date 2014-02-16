package com.rightscale.ssh.launchers.unix;

import com.rightscale.ssh.*;

public class Xterm extends SimpleUnixLauncher {
    public Xterm(Launchpad launchpad) {
        super(launchpad, "xterm -h", "xterm -e");

        if( isPlatform("Mac") ) {
            throw new RuntimeException("Wrong OS.");
        }
    }
}
