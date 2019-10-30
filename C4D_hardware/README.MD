# Building an openC4D
### _The basics about what you have here_

The openC4D is composed of four printed circuit boards (PCBs): the oscillator, the rectifier, and two shielding boards. Inside the folders Oscillator and Rectifier, you will find the schematics and PCB images. The original files were made in [DipTrace](https://diptrace.com/download/download-diptrace/).

Whether you wish a fast track, go straight to Gerber and send the files to some place for the production of a panel with PCBs for one complete openC4D.

### _An warning about the previous version of the printed circuit boards_

The previous version of the PCBs was made available as a panel with the boards for three detectors. There was, however, a small but important failure in the tracks. Unfortunately, two tracks were merged in some point during the production, and now they must be fixed. Figure 1 shows that board and the two small cuts that must be done in the oscillator board. You can use a sharp (and hard) device to cut the merged tracks… and that’s all.
   

![Figure A](https://github.com/claudimir-lago/openC4D/blob/master/C4D_hardware/images/FigA%202019-10-29%20162630.png)
#### _Figure 1._ The red circles show where to cut the tracks. Don’t worry, because there are redundant tracks keeping the right connections between the parts.
   

### _A step-by-step building process (with tips and tricks)_

Although the present files are for one detector, this tutorial is based on the previous paneled version, but don’t worry. As shown in Figure 2, after marking the borders and cutting off the boards, you will have essentially the same boards that you need to build the openC4D.
   

![Figure B](https://github.com/claudimir-lago/openC4D/blob/master/C4D_hardware/images/FigB%202019-10-29%20164422.png)
#### _Figure 2._ We used a CNC machine to make the grooves between all PCBs, but you can use any other method to cut FR04 PCBs. After that, the PCBs are separated, and the borders are sanded accordingly.
   

The four PCBs will be assembled as a “sandwich”, but we need the stuffing for the sandwich: two insulant layers and the ground plane, as shown in Figure 3. These layers will be put between the oscillator and rectifier PCBs and will determine the gap between the electrodes, which essentially determine the detection length.
   

![Figure C](https://github.com/claudimir-lago/openC4D/blob/master/C4D_hardware/images/FigC%202019-10-29%20180601.png)
#### _Figure 3._ The set of seven layers of an openC4D: shield PCBs (a and g), rectifier PCB (b), insulating layers (c and e), copper ground plane (d), and oscillator PCB (f). The copper layer was prepared from a copper foil tape (shown in the inset). After cutting, the copper layer is pulled off and stick to one of the insulating layers.
   

Different materials can be used for the insulating and ground plane layers.

In Figure 3, the material used to make the insulating layers was a 0.25-mm PVC foil – actually, it was previously part of the cover of an old notebook. Obviously, the companies create the cover to cover a document and not necessarily to be used as an insulating layer. We’ve learned that some black covers are not appropriate, because they become somewhat conductive at high frequency (must likely because of the black filler or pigment).

The ground plane can be made of aluminum, copper, brass, or other conductive material. Copper foil tape is convenient, because it is thin and self-adhesive. Thicker foils will increase the coupling of the ground plane with the solution inside the capillary, and it is not good. At the same time, the thin foil of copper can be stick to one of the insulation layer, which ease the alignment of the different layers.

Be careful in making the holes through which the capillary will pass. For instance, a small barb in the copper layer could get into electrical contact with one of the electrodes from either the oscillator or the rectifier PCBs. Make sure, thus, that the holes are clear of barbs to prevent short circuits. 

The PCBs were prepared for SMD components, but there are a two options for the integrated circuits (ICs) U1 and U2 in the rectifier PCB: 5-pin SOT-23 and 8-pin SOIC. Figure 4 shows PCBs populated with these two kinds of components. SOT-23 has a smaller footprint, but SOIC is more popular. The idea was to make a board that could accept these two different versions of operational amplifiers available in the market. There is, however, a very important trick here: **the 5-pin SOT-23 must be soldered upside-down!!!** This was the price to make the board flexible and simple at the same time. Whether you can get the 8-pin SOIC version, buy it because it is safer and usually cheaper solution.
   

![Figure D](https://github.com/claudimir-lago/openC4D/blob/master/C4D_hardware/images/FigD%202019-10-30%20132107.png)
#### _Figure 4._ The rectifier PCB populated with the two versions of U1 and U2. It sounds strange, but the 5-pin SOT-23 ICs must be positioned upside-down before soldering.
   

Although a connector can be soldered at the border of the PCB, we’ve used to solder the cable to the boards, because the final setup is more robust and compact. In this case, make sure that the solder joint has a low profile as shown in Figure 5 in order to prevent that it touches the other PCB. You should try to make a proper [solder joint]( https://learn.adafruit.com/adafruit-guide-excellent-soldering/tools) and cut off the excess after that.
   

![Figure E](https://github.com/claudimir-lago/openC4D/blob/master/C4D_hardware/images/FigE%202019-10-30%20134234.png)
#### _Figure 5._ Pictures of a solder joint before and after the cutoff.
   

Before finishing, it is a good idea to check the voltages and waveforms at the test points.

The wave at the output of U1.2 (Figure 6) should be a square wave swinging between 0 and 5 V at a frequency around 1 MHz. A triangular wave is expected at the output of U1.1 and its amplitude should be about 4 V. At the output of U2 a sine wave is expected with amplitude also of 4 V. Although the original values of the resistors and capacitors were calculated according to these directives, the experimental result is not always the same. Sometimes only minor nonconformities are detected. However, sometimes the result is quite different. Unfortunately, there is no other way but changing resistors and capacitors. It is possible to calculated the values, but it is important having in mind some hints:

•	The higher either R2 or C1, the lower the oscillation frequency;

•	R1/R3 determines the amplitude of the triangular wave;

•	U2.1 with R4, R5, R6, C6, and C7 are a [multiple feedback low-pass filter](http://sim.okawa-denshi.jp/en/OPtazyuLowkeisan.htm) that should cut off the harmonics of the triangular wave and give a small gain to the fundamental frequency.
   

![Figure F](https://github.com/claudimir-lago/openC4D/blob/master/C4D_hardware/images/FigF_Osc-openC4D-Schematic.png)
#### _Figure 6._ Waveforms at the most important test points in the oscillator board.
   

The waveform at the output of the transimpedance amplifier (U1.1 in Figure 7) should be a sine wave with the same frequency of the signal at the output of the oscillator. However, such a signal is detected only if the silica capillary is inserted in the detector. Without the capillary, only a flat line is observed. Figure 7 shows the results with an empty capillary. If the capillary is filled with a conductive solution, the amplitude should be higher.

The waveform at the output of U2.1 is not exactly a sine wave, because of the diodes, but it should respond to the output of the transimpedance amplifier. At TP9 and TP10, the negative and positive half waves, respectively, should be detected. Similar to all other alternated signals showed in these figures, there is a +2.5-V offset.

Finally, after the RC filters (input of the analog-to-digital converter), only flat lines should be detected. 

If everything is OK, it’s time to finish the assembly (Figure 8).
   

![Figure G](https://github.com/claudimir-lago/openC4D/blob/master/C4D_hardware/images/FigG_Rectifier-openC4D-Schematic.png)
#### _Figure 7._ Waveforms at the most important test points in the rectifier board.
   

![Figure H](https://github.com/claudimir-lago/openC4D/blob/master/C4D_hardware/images/FigH_2019-10-30%20163401.png)
#### _Figure 8._ An almost finished openC4D. Please note that the detector has not yet been filled with silicone. We will return to this stage in the future.
   
   
   

#### _Thanks to Eric T. da Costa and Kelliton J. M. Francisco_

