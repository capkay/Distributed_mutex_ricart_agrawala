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

    RAlgorithm(ClientNode cnode, int c_id)
    {
        this.cnode = cnode;
        this.cword = new MEcontrol(c_id);
    }

    public synchronized void request_resource()
    {
        cword.waiting = true;
        cword.our_sn = cword.high_sn + 1;
        for(int j=0;j<cword.N;j++)
        {
            if( (j != cword.ME) & (!cword.A[j]) )
            {
                System.out.println("REQUEST to "+ j);
                cnode.s_list.get(j).crit_request(cword.our_sn);
            }
            if( (j != cword.ME) & (cword.A[j]) )
            {
                System.out.println("REQUEST not needed to "+ j);
            }
        }
        //thread waitforreplies
        //Thread x = new Thread() 
        //{
        //    public void run()
        //    {
                System.out.println("Wait for Replies");
                waitfor_replies();
        //boolean replies_received = false;
        //while(!replies_received)
        //{
        //        for(int j=0;j<cword.N;j++)
        //        {
        //            if( (j != cword.ME) & (cword.A[j] == false) )
        //            {
        //                    System.out.println("AfterWait for Replies : Authorized["+j+"]:"+cword.A[j]);
        //                    replies_received = false;
        //                    break;
        //            }
        //        }
        //}
            System.out.println("Finish Wait for Replies");
        //waitfor (A[j] = true for all j ~ ME);
            cword.waiting = false;
            cword.using = true;
        //    }
        //};
        //x.setDaemon(true);
        //x.start();

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
        while ( !areAllTrue(temp)){
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
                cnode.s_list.get(j).crit_reply();
            }
        }
    }
    public synchronized void process_request_message(int their_sn,int j)
    {
        boolean our_priority;
        cword.high_sn = Math.max(cword.high_sn,their_sn);
        our_priority = (their_sn > cword.our_sn) || ( (their_sn == cword.our_sn) & ( j > cword.ME ) );
        if ( cword.using || ( cword.waiting & our_priority) )
            cword.reply_deferred[j] = true;

	System.out.println("check if need to send reply");
        if ( !(cword.using || cword.waiting) || ( cword.waiting & !cword.A[j] & !our_priority ) )
        {
            System.out.println("REPLY to "+ j+";neither in crit nor requesting");
            cword.A[j] = false;
            cnode.s_list.get(j).crit_reply();
        }

        if ( cword.waiting & cword.A[j] & !our_priority )
        {
            System.out.println("REPLY to "+ j+";optimized");
            cword.A[j] = false;
            cnode.s_list.get(j).crit_reply();
            cnode.s_list.get(j).crit_request(cword.our_sn);
        }

    }
    public synchronized void process_reply_message(int j)
    {
	System.out.println("processing REPLY received from PID "+j);
        cword.A[j] =true;
        cword.notifyAll();
        System.out.println("notifyAll called!");
    }

}
