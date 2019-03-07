import java.util.Date;
import java.util.concurrent.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.LinkedList;
import java.util.*;
import java.lang.management.*;
import java.lang.*;
import java.net.InetAddress;
import java.security.MessageDigest;
import java.text.*;
import java.io.*;
import java.net.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;
import java.util.Set;
public class RAlgorithm
{
    MEcontrol cword = null;
    ClientNode cnode = null;

    RAlgorithm(ClientNode cnode, int c_id, String filename)
    {
        this.cnode = cnode;
        this.cword = new MEcontrol(c_id,filename);
    }

    public synchronized void request_resource()
    {
        cword.waiting = true;
        cword.our_sn = cword.high_sn + 1;
        System.out.println("Request resource timestamp :"+cword.our_sn);

        for(int j=0;j<cword.N;j++)
        {
            if( (j != cword.ME) & (!cword.A[j]) )
            {
                System.out.println("REQUEST to "+ j);
                cnode.c_list.get(j).crit_request(cword.our_sn,cword.filename);
            }
            if( (j != cword.ME) & (cword.A[j]) )
            {
                System.out.println("REQUEST not needed to "+ j);
            }
        }

        System.out.println("Wait for Replies");
        waitfor_replies();

        System.out.println("Finish Wait for Replies");
        cword.waiting = false;
        cword.using = true;
    }

    public static boolean areAllTrue(Boolean[] array)
    {
        for(Boolean b : array) if(!b) return false;
        return true;
    }
    public void waitfor_replies()
    {
        Boolean[] temp = new Boolean[5];
        Arrays.fill(temp, Boolean.FALSE);
        System.out.println("areallTrue start ");
        synchronized(cword)
        {
            for(int i=0;i<5;i++)
                System.out.println("authorized "+cword.A[i]);
        }
        while ( !areAllTrue(temp))
        {
            synchronized(cword)
            {
                temp = cword.A;
            }
        }
        synchronized(cword)
        {
            for(int i=0;i<5;i++)
                System.out.println("authorized "+cword.A[i]);
        }
        System.out.println("areallTrue end");
    }
    public synchronized void release_resource()
    {
        int j;
        cword.using = false;
        for(j=0;j<cword.N;j++)
        {
            if( cword.reply_deferred[j] )
            {
                System.out.println("DEFERRED REPLY sent to "+ j);
                cword.A[j]=false;
                cword.reply_deferred[j]=false;
                cnode.c_list.get(j).crit_reply(cword.filename);
            }
        }
    }
}
