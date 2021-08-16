from bluetooth import *
import base64
import sys
import time
from datetime import datetime 
import subprocess
import threading
from time import sleep
from enum import Enum
import glob
import os
from shutil import copyfile
import shutil
from PIL import Image
import math
from datetime import datetime

class MessageType(Enum):
    IMAGE = 1
    TEXT = 2

killAllThreads = False

# setting up connection with android
server_sock=BluetoothSocket( RFCOMM )
server_sock.bind(("",PORT_ANY))
server_sock.listen(1)
timeout_lock = threading.Lock()
port = server_sock.getsockname()[1]
uuid = "94f39d29-7d6d-437d-973b-fba39e49d4ee"
advertise_service( server_sock, "sample", service_id=uuid)
print( "Waiting for connection on RFCOMM channel %d" % port)
client_sock, client_info = server_sock.accept()
print( "Accepted connection from ", client_info)

# start gphoto2 -> make sure camera is conected
bashCommand = "gphoto2 --shell"
process = subprocess.Popen(bashCommand.split(), stdin=subprocess.PIPE, stdout=subprocess.PIPE, universal_newlines=True, bufsize=0)
last_check = datetime.now()

timeout = 120.0
#can be used to keep the camera alive if it falls asleep
def counter(arg):
    global last_check
    timeout = arg
    timeToKeepAlive = 3.0
    timeToSleep = timeout
    while(True):
        #sleep in shorter cycles
        counter = 0;
        while counter < math.floor(timeToSleep):
            if killAllThreads:
                print("keep alive thread shutting down")
                return 
            sleep(3)
            counter = counter + 3

        with timeout_lock:
            timeSinceLastUpdate = ( datetime.now() - last_check ).total_seconds() 
            if timeSinceLastUpdate > timeout:
                process.stdin.write('set-config capturetarget=1\n')
                last_check = datetime.now()
                print("keep alive")
                sleep(timeToKeepAlive)
                timeToSleep = timeout 
            else:
                timeToSleep = timeout - timeSinceLastUpdate 
            #sys.stdout.flush()

##############
# sending data to android phone functions
def encodeText( text, t ):
    delimiter = "!@#$%^"
    if t == MessageType.IMAGE:
        return delimiter + str(len(text)) +"_" + text
    elif t == MessageType.TEXT:
        return delimiter + str(len(text)) + "=" + text

def encodeImage( filePath ):
    with open( filePath, 'r') as dataFile:
        encoded_string = base64.b64encode(dataFile.read())
    return encodeText( encoded_string, MessageType.IMAGE )


thread = threading.Thread( target = counter, args=(timeout,))
thread.start()


try:
    #keep listening for messages from android phone
    while True:
        data = client_sock.recv(1024)
        if len(data) == 0: break
        print ("received [%s]" % data)
        if  b'fast picture' in data:
            print("fast picture mode")
            with timeout_lock:
		#save to sd card
                process.stdin.write('set-config capturetarget=1\n')
                process.stdin.write('trigger-capture\n')
                message = "One picture taken"
                client_sock.send(encodeText(message, MessageType.TEXT))
                last_check = datetime.now()

        elif data == "one picture":
            with timeout_lock:
                process.stdin.write('capture-image-and-download\n')
                message = "One picture taken"
                client_sock.send(encodeText(message, MessageType.TEXT))
                time.sleep(5) 
                
		#sending the image back to android phone for review
                list_of_files = glob.glob('*.JPG') 
                if len(list_of_files) > 0:
                    latest_file = max(list_of_files, key=os.path.getctime)
                    foo = Image.open( latest_file )
                    x,y = foo.size
                    x2,y2 = int(math.floor(x/2)),int(math.floor(y/2))
                    foo = foo.resize((x2,y2),Image.ANTIALIAS)
                    foo.save( latest_file  )
                    client_sock.send(encodeImage(latest_file))
                    os.remove( latest_file )
                last_check = datetime.now()

        elif "multiple pictures" in data:
            arguments = str.split(data);
            pictureCount = int(arguments[2])
            intervalCount = int(arguments[3])
            with timeout_lock:
                for i in range(0,pictureCount):
                    print("picture " + str(i))
                    process.stdin.write('set-config capturetarget=1\n')
                    process.stdin.write('trigger-capture\n')
                    #or you could save it to Raspberry pi too using line below
                    #process.stdin.write('capture-image-and-download\n')
                    message = ""+str(i+1)+"/"+str(pictureCount)
                    client_sock.send(encodeText( message, MessageType.TEXT ))

                    last_check = datetime.now()
                    time.sleep(intervalCount) 
        elif "shutdown" in data:
            break

except IOError:
    pass

print( "disconnected" )
killAllThreads = True
client_sock.close()
server_sock.close()
thread.join()
print("all done")
