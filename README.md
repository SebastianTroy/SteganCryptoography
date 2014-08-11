SteganCryptoography
===================

A program that uses steganography to hide messages inside images but in such a way as to make it impossible to tell random data from our message.

The concept of the program is that a Neural Network is created that reads 16 bits of information and translates it into a letter. 2^16 = 65536 different possible characters in our alphabet however the network will narrow the inputs down to a letter of the alphabet a-z, a space ' ' or a terminating character that signals the end of the message, a total of 28. This makes our origional 16 bits very redundant in that there are roughly 2340 combinations per letter of the alphabet making it highly unlikely that in a single message any combination will ever be repeated.

The information is hidden in the picture in 4*4 squares. Each pixel of the image is used to encode 1 bit, if the RGB integer for a pixel is even it is == 0, if it is odd then it is == 1. Due to the nature of the encryption, compression of the image will completely destroy any hidden message.

How To Use
==========
Simple
------
 - Rename a .png or .jpg to a message that you want hidden inside it
 - Run the program (From the same directory as the image is the easiest way to locate your image file)
 - Select the image and click "OK"
 - Click "Cancel" to finish or simply select another file to encode
 - You will find your image has been renamed, you can now change it to whatever you like and your secret message is safe

 - To retrieve your message Run the program again
 - Select the image and click "OK"
 - Click "Cancel" to finish or simply select another file to decode
 - You will find your image has been renamed back to the origional message that you encoded (minus punctuation or capital letters)

Advanced
--------
To run the program from a command line or console, open the console to the location of the program and type "java -jar Encrypto.jar"

You can also add extra arguments to the JVM for extra features of the program, "java -jar [add your arguments here] Encrypto.jar"

these are:
 - '-Dseed=<i>integer</i>' This allows you to specify your own seed, used to construct the Neural Network, messages encoded with a seed can only be decoded with exactly the same seed allowing for "passkey" protected messages (default value if unspecified == 1198662804)
 - '-Dleeway=<i>float</i>' This allows you to controll the inner workings of the Network used to encode and decode messages, again changing this value will change how the network translates information. This value is very sensitive and it is reccomended that you use some of the other modes in order to refine this variable if you intend to change it. (default value if unspecified == 0.07)
 - '-Dmode=<i>mode</i>' This allows you to change what mode the program is run in which are:
   - <b>dictionary</b>, this mode simply cycles through every combination 16 bits can represent and prints out the character that the Network represents it as. This can be helpful in assesing how randomly our Network is assigning characters to our 16 bit combinations
   - <b>stats</b>, this mode calculates the frequency of combinations that represent each letter of the alphabet, it then prints out the Standard Deviation of the frequencies and also an arrayrepresenting the % of combinations used for each letter. A psuedo random distribution of frequencies would result in a standard deviation of roughly 100 though 400 is a good number to aim for when chosing a seed.
   - <b>optimise</b>, this mode allows you to search for a seed with a low standard deviation for a particular setup, you can specify the 'levels' used in the Network and the 'leeway' with the above parameters.
   - <b>graph</b>, this mode draws a graph to an image and saves it to your desktop. This mode is most useful for narrowing down an appropriate 'leeway' value. The graph shows the standard deviation for a range of Networks with leeways set between
     - '-Dminleeway' (default if unspecified == 0.0)
     - '-Dmaxleeway' (default if unspecified == 0.4)
