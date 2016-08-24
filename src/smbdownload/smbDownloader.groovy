#!/usr/bin/env groovy

package smbdownload

import java.io.File
import jcifs.smb.NtlmPasswordAuthentication
import jcifs.smb.SmbFile
import jcifs.smb.SmbFileInputStream

/**
 * returns the percent between the lengths of two files
 **/
def percent(file1,file2){
    Math.round(file1.length() / file2.length() * 100)
}

/**
 * prints the progress bar
 **/
def printProgress(percent){
    print "["
    (0..Math.round(percent / 10)).each{
        print "|"
    }

    (Math.round(percent/10)..10).each{
        print " "
    }
    
    print "] ${percent}% "
    print "\r"
}


/**
 * downloads a SmbFile to a Path
 * only used to download files, not directories 
 **/
def downloadFile(SmbFile file, String path, int delay){
    try{
        file.connect()
        def newfile = new File(path+"/"+file.getName())
        def f = new FileOutputStream(newfile,true)
        def is = new BufferedInputStream(file.getInputStream())
        
        def buf = new byte[1024*90]
        println "\ndownloading..."
        is.skip(newfile.length())
        
        readf = is.read(buf)
        while (readf > 0){
            printProgress(percent(newfile,file))
            f.write(buf,0,readf)
            if (delay > 0){
                sleep(delay)
            }
            readf = is.read(buf)
        }
        f.close()
        println "[||||||||||||] 100%"
        println "file saved to ${path}"
        println "[ok]"
        return true
    } catch (Exception e){
        println e.getMessage()
        return false
    }
}

/**
 * lists all files on a base directory
 **/
def getFilesFromDir(SmbFile baseDir) {
    SmbFile[] files = baseDir.listFiles()
    List results = new ArrayList()
   
    for (SmbFile file : files) {
        if (file.isDirectory()) {
            results.addAll(getFilesFromDir(file))
        } else {
            results.add(file)
        }
    }
    return results
}


/**
 * writes the configuration file with .magneto_task extension
 * in order to resume the task
 **/
def writeConf(url, pathToSave, delay, ua, user = "", password = ""){
    String aux = (String)url
    
    while (aux.endsWith("/")){
        aux = aux.substring(0,aux.length()-1)
    }
    aux = aux.substring(aux.lastIndexOf("/"),aux.length())
    println "writing config to ${pathToSave}${aux}.magneto_task"
    File fo = new File(pathToSave+"/"+aux+".magneto_task")

    fo.append(url+"\n")
    fo.append(pathToSave+"\n")
    fo.append(String.valueOf(delay)+"\n")
    fo.append(ua+"\n")
    if (ua == "y"){
        fo.append(user+"\n")
        fo.append(password+"\n")
    }
    
}

public static void main (args) {
    println "Magneto v1.3 by \$h@"
    println "download from smb servers without loosing track of your files\n"
    Console sc = System.console()
    String url = ""
    def pathToSave = "."
    def delay = "0"
    def ua = "n"
    def user = ""
    def password = ""
    if (args.length > 0){
        File file = new File(args[0])

        try {
            BufferedReader br = new BufferedReader(new FileReader(file))
            
            url = br.readLine()
            pathToSave = br.readLine()
            delay = Integer.valueOf(br.readLine())
            ua = br.readLine()
            if (ua == "y"){
                user = br.readLine()
                password = br.readLine()            
            }
            br.close()

        } catch (IOException e) {
        }
    } else {
        url = sc.readLine("url: ")
        pathToSave = sc.readLine("path to save: ")
        delay = Integer.valueOf(sc.readLine("delay(miliseconds): "))
        ua = sc.readLine("use auth (y/n): ")
    }
    
    SmbFile file = null

    if (ua.equals("y")){
        if (user == ""){
            user = sc.readLine("user: ")
        }
        if (password == ""){
            password = new String(sc.readPassword("Password: "))
        }
        NtlmPasswordAuthentication auth = new NtlmPasswordAuthentication("", user, password)
        file = new SmbFile(url,auth)
    } else {
        file = new SmbFile(url)
    }
    
    if (args.length <= 0){
        writeConf(url,pathToSave, delay, ua, user, password)
    }
    
    try{
        if (!file.exists()){
            println "can't find file ${url}"
            System.exit(1)
        }
        if (file.isDirectory()) {
            print "listing files...\t"
            def filelist = getFilesFromDir(file)
            println "[ok]"
        
            print "populating dirs...\t"
        
            pathToSave += file.getName()
            new File(pathToSave).mkdirs()
            for (SmbFile f in filelist){
                new File(pathToSave+"/"+f.getParent().replace(url,"")).mkdirs()
            }
            println "[ok]"
        
            def c = 0
            for (SmbFile f in filelist){
                println "file ${++c} of ${filelist.size}"
                println "starting download for: ${f.getPath()}"
                while (!downloadFile(f,pathToSave+"/"+f.getParent().replace(url,""),delay)){
                    sleep(100)
                }
            }
            
            println "[all done]"
        
        } else if (file.isFile()){
            println "starting download for: ${file.getName()}"
            while (!downloadFile(file,pathToSave,delay)){
                sleep(100)
            }
        }
    } catch (jcifs.smb.SmbException e){
        println "${e.message} [failed]"
    } catch (java.net.UnknownHostException e){
        println "Unknow host [failed]"
    }
}