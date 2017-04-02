# Description

Read/parsing the output of my "Drehstromzähler" eHZ E3L - AW11112 using the IR optical head from [volkszaehler.org](http://wiki.volkszaehler.org/hardware/controllers/ir-schreib-lesekopf).

Goal: Push the output to an InfluxDB instance. 

## Getting started

Build the binary:
```bash
git clone https://github.com/adiesner/eHzLogger.git
cd eHzLogger
mvn clean package assembly:assembly
```

Copy jar to your Raspberry Pi:
```bash
scp target/eHzLogger*-jar-with-dependencies.jar username@raspberrypi:~
```

Install Java on Raspberry Pi:
```bash
sudo apt install openjdk-8-jre librxtx-java
```

Install the IR head to your eHZ. Connect it with your Raspberry Pi.

Test the connection (should output some hex code every second):
```bash
sudo chmod 777 /dev/ttyUSB0
stty -F /dev/ttyUSB0 1:0:8bd:0:3:1c:7f:15:4:5:1:0:11:13:1a:0:12:f:17:16:0:0:0:0:0:0:0:0:0:0:0:0:0:0:0:0
cat /dev/ttyUSB0 | od -tx1
```

Start eHzLogger on Raspberry Pi
```bash
java -jar eHzLogger*-jar-with-dependencies.jar
```

Should output every second
```bash
Got SML_File
Server-ID: 00:00:00:00:00:00:00:00:00:00
1-0:1.8.0*255 = 5453232.6 Wh
1-0:1.8.1*255 = 5453232.6 Wh
1-0:1.8.2*255 = 0.0 Wh
1-0:16.7.0*255 = 39.9 W

```

## Links
* Library to parse SML protocol: https://github.com/n-st/collectd-sml

### Other Projects
* Worked but regex did not match for my EHZ E3L: https://github.com/z0mt3c/ehz-sml-reader