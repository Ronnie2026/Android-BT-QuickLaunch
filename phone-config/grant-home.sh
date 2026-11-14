#!/system/bin/sh
magisk --sqlite 'REPLACE INTO policies (uid,policy,until,logging,notification) VALUES (10203,2,0,1,0);'
magisk --sqlite 'SELECT uid,policy,until FROM policies WHERE uid=10203;'
