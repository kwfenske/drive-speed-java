/*
  Drive Speed #1 - Read/Write Speeds for Disk Drives, Flash Drives
  Written by: Keith Fenske, http://kwfenske.github.io/
  Monday, 31 October 2016
  Java class name: DriveSpeed1
  Copyright (c) 2016 by Keith Fenske.  Apache License or GNU GPL.

  This is a Java 1.4 graphical (GUI) application to test the speed of disk
  drives or flash drives.  Large temporary files are written with all zero
  bytes, then read back.  To get accurate results, files must be bigger than
  the amount of physical memory on your computer (RAM), and should be several
  times bigger, because your computer uses some of its memory as a "disk cache"
  to increase the apparent speed of drives.  Large files minimize the effects
  of cache.  There will be some variation in results, around ten percent over
  minutes and five percent over hours.  Likely factors are:

   1. Hardware speed of disk drive, motherboard, and connections.  CPU speed is
      not a major concern for this program.
   2. Java version and operating system (Linux, MacOS, Windows, etc).
   3. Some file systems (NTFS) may be faster than others (FAT32).  Newly
      formatted disks should be fully written once before testing.
   4. Disk drives may slow down on continuous activity to reduce heat.  Flash
      drives can get slower with usage.  (Repeated testing may degrade flash
      drives.)
   5. Other active programs consume CPU time and disk I/O, including screen
      savers, anti-virus products, and automatic updates.

  When you run this program, choose your options, and click the "Drive Folder"
  button to select a folder (directory) where the program can write one or more
  temporary files.  This can be anywhere on the drive to be tested, where you
  have write access.  Click the "Start" button to begin.  The first test is for
  writing only.  The progress bar above the "Start" button shows how much data
  has been written.  The "Write Speed" box in the bottom right-hand corner
  shows the current write speed, and the final average write speed.  After
  writing is finished, the same file(s) will be read.  Data is not checked for
  being all zero when read, because that would take extra time.  The "Read
  Speed" box in the bottom left-hand corner shows the current read speed, and
  the final average read speed when finished.

  The size of the data buffer is an option.  You rarely need to change this,
  unless you suspect that the computer system is not doing well with a certain
  size.  Buffer size is the number of bytes read or written on each request to
  the system.  Most computers handle a wide range of sizes with equal
  performance.  The default buffer size is generally good.

  Being prompted with a pop-up dialog box is an option, after writing finishes
  and before reading starts.  If your drive is a removable device or on
  removable media, you can remove (eject) the drive by the normal procedure for
  your system, wait a few seconds, reinsert the drive, and continue.  This
  clears the disk cache, and is important for USB thumb drives, which are often
  smaller than the amount of memory on your computer.

  Don't use this program on compressed disks, because zeros are constant and
  highly compressible.  Files with names similar to "ERASE123.DAT" are assumed
  to belong to this program and will be replaced or deleted without notice.
  See also the EraseDisk Java application.

  Apache License or GNU General Public License
  --------------------------------------------
  DriveSpeed1 is free software and has been released under the terms and
  conditions of the Apache License (version 2.0 or later) and/or the GNU
  General Public License (GPL, version 2 or later).  This program is
  distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY,
  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
  PARTICULAR PURPOSE.  See the license(s) for more details.  You should have
  received a copy of the licenses along with this program.  If not, see the
  http://www.apache.org/licenses/ and http://www.gnu.org/licenses/ web pages.

  Graphical Versus Console Application
  ------------------------------------
  The Java command line may contain options for the position and size of the
  application window, and the size of the display font.  See the "-?" option
  for a help summary:

      java  DriveSpeed1  -?

  The command line has more options than are visible in the graphical
  interface.  An option such as -u14 or -u16 is recommended because the default
  Java font is too small.

  Restrictions and Limitations
  ----------------------------
  Read speeds will be meaningless if the total number of bytes written is
  smaller than the physical memory (RAM) on a computer, as data may actually be
  "read" from the computer's disk cache and not from the drive.  FAT32-
  formatted volumes (i.e., USB thumb drives) have a maximum size of 4 GB per
  file, unlike exFAT or NTFS.  Windows 2000/XP/Vista/7 tends to misallocate a
  few clusters when large FAT32 volumes are nearly full or files reach their
  maximum size; these show up later as "lost" single-cluster files in CHKDSK.

  Suggestions for New Features
  ----------------------------
  (1) Without a proper way of clearing the disk cache (as with removable
      media), read speeds are often over-inflated.  KF, 2016-11-25.
*/

import java.awt.*;                // older Java GUI support
import java.awt.event.*;          // older Java GUI event support
import java.io.*;                 // standard I/O
import java.text.*;               // number formatting
import java.util.regex.*;         // regular expressions
import javax.swing.*;             // newer Java GUI support
import javax.swing.border.*;      // decorative borders

public class DriveSpeed1
{
  /* constants */

  static final String COPYRIGHT_NOTICE =
    "Copyright (c) 2016 by Keith Fenske.  Apache License or GNU GPL.";
  static final int DEFAULT_HEIGHT = -1; // default window height in pixels
  static final int DEFAULT_LEFT = 50; // default window left position ("x")
  static final int DEFAULT_TOP = 50; // default window top position ("y")
  static final int DEFAULT_WIDTH = -1; // default window width in pixels
  static final int ERASE_NONE = 0; // current state in startErase() method
  static final int ERASE_READ = 1;
  static final int ERASE_WRITE = 2;
  static final String[] IGNORE_CHOICES = {"2", "5", "10", "20", "50", "100",
    "200"};                       // number of seconds to ignore when starting
  static final String IGNORE_DEFAULT = "5"; // default choice for above
  static final int MAX_FILE_COUNT = 999; // maximum number of temporary files
  static final long MAX_FILE_SIZE = 0x7FFFFFFF00000000L;
                                  // safe positive 64-bit integer
  static final int MIN_FRAME = 200; // minimum window height or width in pixels
  static final String NO_DRIVE_TEXT =
    "Please select a drive folder for writing files.";
  static final String NO_SPEED_TEXT = ""; // used before speeds are calculated
  static final String PROGRAM_TITLE =
    "Read/Write Speeds for Disk Drives, Flash Drives - by: Keith Fenske";
  static final String SYSTEM_FONT = "Dialog"; // this font is always available
  static final int TIMER_DELAY = 1000; // 1.000 seconds between status updates
  static final String TOO_FAST_TEXT = "zero time";
                                  // when not enough time to calculate speed;
                                  // see also: http://en.wikipedia.org/wiki/NaN

  /* The queue size for pending clock data is affected by both IGNORE_CHOICES
  and TIMER_DELAY because you need one entry in the queue for each timer event,
  up to the total number of seconds being ignored, with a few to spare.  The
  modular arithmetic (%) is probably faster with a power of two. */

  static final int QUEUE_SIZE = 256; // maximum size of pending clock data

  /* class variables */

  static JRadioButton buffer1Kbutton, buffer2Kbutton, buffer4Kbutton,
    buffer8Kbutton, buffer16Kbutton, buffer32Kbutton, buffer64Kbutton,
    buffer128Kbutton, buffer256Kbutton, buffer512Kbutton, buffer1Mbutton,
    buffer2Mbutton, buffer4Mbutton, buffer8Mbutton, buffer16Mbutton,
    buffer32Mbutton, buffer64Mbutton, buffer128Mbutton, buffer256Mbutton,
    buffer512Mbutton;             // radio buttons for data buffer sizes
  static long bytesAllFiles;      // current number of bytes read or written
  static JButton cancelButton;    // graphical button for <cancelFlag>
  static boolean cancelFlag;      // our signal from user to stop processing
  static boolean deleteFlag;      // true if we delete our temporary files
  static JButton driveFolderButton; // button to select where we write files
  static File driveSelection;     // user's selected writeable drive folder
  static int eraseState;          // current state in startErase() method
  static JButton exitButton;      // "Exit" button for ending this application
  static JRadioButton file1Mbutton, file10Mbutton, file100Mbutton,
    file1Gbutton, file10Gbutton, file100Gbutton, file1Tbutton, file10Tbutton,
    file100Tbutton, file1Pbutton, file10Pbutton, file100Pbutton, file1Ebutton,
    fileMaximumButton;            // radio buttons for temporary file size
  static JFileChooser fileChooser; // asks for input and output file names
  static NumberFormat formatComma; // formats with commas (digit grouping)
  static NumberFormat formatPointOne; // formats with one decimal digit
  static JCheckBox ignoreCheckbox; // GUI enable option "ignore first seconds"
  static long ignoreDelayMillis;  // start/stop delay time in milliseconds
  static JComboBox ignoreDialog;  // GUI select number of seconds to ignore
  static long ignoreStartBytes;   // number of bytes after start delay
  static long ignoreStartClock;   // clock milliseconds after start delay
  static long ignoreStopBytes;    // number of bytes before stop delay
  static long ignoreStopClock;    // clock milliseconds before stop delay
  static JFrame mainFrame;        // this application's window if GUI
  static long maxDataBytes;       // maximum total bytes, all temporary files
  static boolean mswinFlag;       // true if running on Microsoft Windows
  static JProgressBar progressBar; // progress bar and status text
  static JCheckBox promptCheckbox; // if we prompt user before reading
  static long[] queueDataBytes;   // paired pending clock data: byte count
  static long[] queueDataClock;   // paired pending clock data: clock time
  static int queueFirstIndex;     // index of first (oldest) item in queue
  static int queueItemCount;      // total number of items in pending queue
  static JTextField readSpeedText; // average read speed in bytes per second
  static JButton startButton;     // "Start" button to begin file processing
  static long startTime;          // starting milliseconds for read/write pass
  static javax.swing.Timer statusTimer; // timer for updating status message
  static long totalBytesWritten;  // total number of bytes written, all files
  static long userBytesPrev;      // previous number of bytes reported
  static double userBytesRate;    // current or previous bytes per second
  static JTextField writeSpeedText; // average write speed in bytes per second

/*
  main() method

  We run as a graphical application only.  Set the window layout and then let
  the graphical interface run the show.
*/
  public static void main(String[] args)
  {
    ActionListener action;        // our shared action listener
    Font buttonFont;              // font for buttons, labels, status, etc
    int i;                        // index variable
    boolean maximizeFlag;         // true if we maximize our main window
    Font speedFont;               // font for drive speeds only (bigger)
    int windowHeight, windowLeft, windowTop, windowWidth;
                                  // position and size for <mainFrame>
    String word;                  // one parameter from command line

    /* Initialize variables used by both console and GUI applications. */

    buttonFont = null;            // by default, don't use customized font
//  buttonFont = new Font(SYSTEM_FONT, Font.PLAIN, 16); // force default font
    cancelFlag = false;           // don't cancel unless user complains
    driveSelection = null;        // there is no writeable drive folder yet
    eraseState = ERASE_NONE;      // current state in startErase() method
    maximizeFlag = false;         // by default, don't maximize our main window
    mswinFlag = System.getProperty("os.name").startsWith("Windows");
    queueDataBytes = queueDataClock = null; // no pending bytes/clock data
    speedFont = null;             // by default, don't use customized font
//  speedFont = new Font(SYSTEM_FONT, Font.PLAIN, 24); // force default font
    windowHeight = DEFAULT_HEIGHT; // default window position and size
    windowLeft = DEFAULT_LEFT;
    windowTop = DEFAULT_TOP;
    windowWidth = DEFAULT_WIDTH;

    /* Initialize number formatting styles. */

    formatComma = NumberFormat.getInstance(); // current locale
    formatComma.setGroupingUsed(true); // use commas or digit groups

    formatPointOne = NumberFormat.getInstance(); // current locale
    formatPointOne.setGroupingUsed(true); // use commas or digit groups
    formatPointOne.setMaximumFractionDigits(1); // force one decimal digit
    formatPointOne.setMinimumFractionDigits(1);

    /* Check command-line parameters for options. */

    for (i = 0; i < args.length; i ++)
    {
      word = args[i].toLowerCase(); // easier to process if consistent case
      if (word.length() == 0)
      {
        /* Ignore empty parameters, which are more common than you might think,
        when programs are being run from inside scripts (command files). */
      }

      else if (word.equals("?") || word.equals("-?") || word.equals("/?")
        || word.equals("-h") || (mswinFlag && word.equals("/h"))
        || word.equals("-help") || (mswinFlag && word.equals("/help")))
      {
        showHelp();               // show help summary
        System.exit(0);           // exit application after printing help
      }

      else if (word.startsWith("-u") || (mswinFlag && word.startsWith("/u")))
      {
        /* This option is followed by a font point size that will be used for
        buttons, dialogs, labels, etc. */

        int size = -1;            // default value for font point size
        try                       // try to parse remainder as unsigned integer
        {
          size = Integer.parseInt(word.substring(2));
        }
        catch (NumberFormatException nfe) // if not a number or bad syntax
        {
          size = -1;              // set result to an illegal value
        }
        if ((size < 10) || (size > 99))
        {
          System.err.println("Dialog font size must be from 10 to 99: "
            + args[i]);           // notify user of our arbitrary limits
          showHelp();             // show help summary
          System.exit(-1);        // exit application after printing help
        }
        buttonFont = new Font(SYSTEM_FONT, Font.PLAIN, size); // for big sizes
//      buttonFont = new Font(SYSTEM_FONT, Font.BOLD, size); // for small sizes
        speedFont = new Font(SYSTEM_FONT, Font.PLAIN, (int) (size * 1.5));
      }

      else if (word.startsWith("-w") || (mswinFlag && word.startsWith("/w")))
      {
        /* This option is followed by a list of four numbers for the initial
        window position and size.  All values are accepted, but small heights
        or widths will later force the minimum packed size for the layout. */

        Pattern pattern = Pattern.compile(
          "\\s*\\(\\s*(\\d{1,5})\\s*,\\s*(\\d{1,5})\\s*,\\s*(\\d{1,5})\\s*,\\s*(\\d{1,5})\\s*\\)\\s*");
        Matcher matcher = pattern.matcher(word.substring(2)); // parse option
        if (matcher.matches())    // if option has proper syntax
        {
          windowLeft = Integer.parseInt(matcher.group(1));
          windowTop = Integer.parseInt(matcher.group(2));
          windowWidth = Integer.parseInt(matcher.group(3));
          windowHeight = Integer.parseInt(matcher.group(4));
        }
        else                      // bad syntax or too many digits
        {
          System.err.println("Invalid window position or size: " + args[i]);
          showHelp();             // show help summary
          System.exit(-1);        // exit application after printing help
        }
      }

      else if (word.equals("-x") || (mswinFlag && word.equals("/x")))
        maximizeFlag = true;      // true if we maximize our main window

      else                        // parameter is not a recognized option
      {
        System.err.println("Option not recognized: " + args[i]);
        showHelp();               // show help summary
        System.exit(-1);          // exit application after printing help
      }
    }

    /* Open the graphical user interface (GUI).  The standard Java style is the
    most reliable, but you can switch to something closer to the local system,
    if you want. */

//  try
//  {
//    UIManager.setLookAndFeel(
//      UIManager.getCrossPlatformLookAndFeelClassName());
////    UIManager.getSystemLookAndFeelClassName());
//  }
//  catch (Exception ulafe)
//  {
//    System.err.println("Unsupported Java look-and-feel: " + ulafe);
//  }

    /* Initialize shared graphical objects. */

    action = new DriveSpeed1User(); // create our shared action listener
    fileChooser = new JFileChooser(); // create our shared file chooser
    statusTimer = new javax.swing.Timer(TIMER_DELAY, action);
                                  // update status message on clock ticks only

    /* Create the graphical interface as a series of little panels inside
    bigger panels.  The intermediate panel names are of no lasting importance
    and hence are only numbered (panel01, panel02, etc). */

    /* Create a vertical box to stack buttons and options. */

    JPanel panel01 = new JPanel();
    panel01.setLayout(new BoxLayout(panel01, BoxLayout.Y_AXIS));

    JPanel panel02 = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
    JLabel label03 = new JLabel("Drive Speed Test");
    if (buttonFont != null) label03.setFont(buttonFont);
    panel02.add(label03);         // label doesn't center properly by itself
    panel01.add(panel02);         // so put label inside panel with center
    panel01.add(Box.createVerticalStrut(10)); // space between panels

    /* Options for changing the data buffer size.  The user interface looks
    best with five choices enabled.  One of those should be pre-selected.

    The largest buffer size that normally works is 64 MB in Java 1.4 to 7, if
    the run-time heap size is also sufficiently large (the default for Java 6
    or later).  Buffers from 128 to 512 MB sometimes work when the heap is
    bigger but not too big (?).  The 1 GB size never worked: an "out of memory"
    exception occurs in the java.io.FileOutputStream.write() native method. */

    JPanel panel11 = new JPanel();
    panel11.setBorder(BorderFactory.createTitledBorder(null,
      " Data Buffer Size ", TitledBorder.DEFAULT_JUSTIFICATION,
      TitledBorder.DEFAULT_POSITION, buttonFont));
    panel11.setLayout(new FlowLayout(FlowLayout.CENTER, 20, 0));
//  panel11.setLayout(new GridLayout(0, 5)); // if more than 6 choices
    panel11.setToolTipText("Size of read-write data buffer in bytes.");

    ButtonGroup group12 = new ButtonGroup();

    buffer1Kbutton = new JRadioButton("1 KB");
    if (buttonFont != null) buffer1Kbutton.setFont(buttonFont);
    group12.add(buffer1Kbutton);
//  panel11.add(buffer1Kbutton);  // currently disabled: too small

    buffer2Kbutton = new JRadioButton("2 KB");
    if (buttonFont != null) buffer2Kbutton.setFont(buttonFont);
    group12.add(buffer2Kbutton);
//  panel11.add(buffer2Kbutton);  // currently disabled: too small

    buffer4Kbutton = new JRadioButton("4 KB");
    if (buttonFont != null) buffer4Kbutton.setFont(buttonFont);
    group12.add(buffer4Kbutton);
    panel11.add(buffer4Kbutton);

    buffer8Kbutton = new JRadioButton("8 KB");
    if (buttonFont != null) buffer8Kbutton.setFont(buttonFont);
    group12.add(buffer8Kbutton);
//  panel11.add(buffer8Kbutton);  // currently disabled: too similar

    buffer16Kbutton = new JRadioButton("16 KB");
    if (buttonFont != null) buffer16Kbutton.setFont(buttonFont);
    group12.add(buffer16Kbutton);
    panel11.add(buffer16Kbutton);

    buffer32Kbutton = new JRadioButton("32 KB");
    if (buttonFont != null) buffer32Kbutton.setFont(buttonFont);
    group12.add(buffer32Kbutton);
//  panel11.add(buffer32Kbutton); // currently disabled: too similar

    buffer64Kbutton = new JRadioButton("64 KB");
    if (buttonFont != null) buffer64Kbutton.setFont(buttonFont);
    group12.add(buffer64Kbutton);
    panel11.add(buffer64Kbutton);

    buffer128Kbutton = new JRadioButton("128 KB");
    if (buttonFont != null) buffer128Kbutton.setFont(buttonFont);
    group12.add(buffer128Kbutton);
//  panel11.add(buffer128Kbutton); // currently disabled: too similar

    buffer256Kbutton = new JRadioButton("256 KB", true);
    if (buttonFont != null) buffer256Kbutton.setFont(buttonFont);
    group12.add(buffer256Kbutton);
    panel11.add(buffer256Kbutton);

    buffer512Kbutton = new JRadioButton("512 KB");
    if (buttonFont != null) buffer512Kbutton.setFont(buttonFont);
    group12.add(buffer512Kbutton);
//  panel11.add(buffer512Kbutton); // currently disabled: too similar

    buffer1Mbutton = new JRadioButton("1 MB");
    if (buttonFont != null) buffer1Mbutton.setFont(buttonFont);
    group12.add(buffer1Mbutton);
    panel11.add(buffer1Mbutton);

    buffer2Mbutton = new JRadioButton("2 MB");
    if (buttonFont != null) buffer2Mbutton.setFont(buttonFont);
    group12.add(buffer2Mbutton);
//  panel11.add(buffer2Mbutton);  // currently disabled: too similar

    buffer4Mbutton = new JRadioButton("4 MB");
    if (buttonFont != null) buffer4Mbutton.setFont(buttonFont);
    group12.add(buffer4Mbutton);
    panel11.add(buffer4Mbutton);

    buffer8Mbutton = new JRadioButton("8 MB");
    if (buttonFont != null) buffer8Mbutton.setFont(buttonFont);
    group12.add(buffer8Mbutton);
//  panel11.add(buffer8Mbutton);  // currently disabled: too similar

    buffer16Mbutton = new JRadioButton("16 MB");
    if (buttonFont != null) buffer16Mbutton.setFont(buttonFont);
    group12.add(buffer16Mbutton);
//  panel11.add(buffer16Mbutton); // currently disabled: too big

    buffer32Mbutton = new JRadioButton("32 MB");
    if (buttonFont != null) buffer32Mbutton.setFont(buttonFont);
    group12.add(buffer32Mbutton);
//  panel11.add(buffer32Mbutton); // currently disabled: too big

    buffer64Mbutton = new JRadioButton("64 MB");
    if (buttonFont != null) buffer64Mbutton.setFont(buttonFont);
    group12.add(buffer64Mbutton);
//  panel11.add(buffer64Mbutton); // currently disabled: too big

    buffer128Mbutton = new JRadioButton("128 MB");
    if (buttonFont != null) buffer128Mbutton.setFont(buttonFont);
    group12.add(buffer128Mbutton);
//  panel11.add(buffer128Mbutton); // currently disabled: too big

    buffer256Mbutton = new JRadioButton("256 MB");
    if (buttonFont != null) buffer256Mbutton.setFont(buttonFont);
    group12.add(buffer256Mbutton);
//  panel11.add(buffer256Mbutton); // currently disabled: too big

    buffer512Mbutton = new JRadioButton("512 MB");
    if (buttonFont != null) buffer512Mbutton.setFont(buttonFont);
    group12.add(buffer512Mbutton);
//  panel11.add(buffer512Mbutton); // currently disabled: too big

    panel01.add(panel11);
    panel01.add(Box.createVerticalStrut(10)); // space between panels

    /* Options for changing the total size of all temporary files.  Five
    choices look best, and one should be pre-selected. */

    JPanel panel21 = new JPanel();
    panel21.setBorder(BorderFactory.createTitledBorder(null,
      " Temporary File Size ", TitledBorder.DEFAULT_JUSTIFICATION,
      TitledBorder.DEFAULT_POSITION, buttonFont));
    panel21.setLayout(new FlowLayout(FlowLayout.CENTER, 20, 0));
//  panel21.setLayout(new GridLayout(0, 5)); // if more than 6 choices
    panel21.setToolTipText("Total number of bytes to write, then read.");

    ButtonGroup group22 = new ButtonGroup();

    file1Mbutton = new JRadioButton("1 MB"); // megabyte (10**6)
    if (buttonFont != null) file1Mbutton.setFont(buttonFont);
    group22.add(file1Mbutton);
//  panel21.add(file1Mbutton);    // currently disabled: too small

    file10Mbutton = new JRadioButton("10 MB");
    if (buttonFont != null) file10Mbutton.setFont(buttonFont);
    group22.add(file10Mbutton);
//  panel21.add(file10Mbutton);   // currently disabled: too small

    file100Mbutton = new JRadioButton("100 MB");
    if (buttonFont != null) file100Mbutton.setFont(buttonFont);
    group22.add(file100Mbutton);
//  panel21.add(file100Mbutton);  // currently disabled: too small

    file1Gbutton = new JRadioButton("1 GB"); // gigabyte (10**9)
    if (buttonFont != null) file1Gbutton.setFont(buttonFont);
    group22.add(file1Gbutton);
    panel21.add(file1Gbutton);

    file10Gbutton = new JRadioButton("10 GB");
    if (buttonFont != null) file10Gbutton.setFont(buttonFont);
    group22.add(file10Gbutton);
    panel21.add(file10Gbutton);

    file100Gbutton = new JRadioButton("100 GB", true);
    if (buttonFont != null) file100Gbutton.setFont(buttonFont);
    group22.add(file100Gbutton);
    panel21.add(file100Gbutton);

    file1Tbutton = new JRadioButton("1 TB"); // terabyte (10**12)
    if (buttonFont != null) file1Tbutton.setFont(buttonFont);
    group22.add(file1Tbutton);
    panel21.add(file1Tbutton);

    file10Tbutton = new JRadioButton("10 TB");
    if (buttonFont != null) file10Tbutton.setFont(buttonFont);
    group22.add(file10Tbutton);
    panel21.add(file10Tbutton);

    file100Tbutton = new JRadioButton("100 TB");
    if (buttonFont != null) file100Tbutton.setFont(buttonFont);
    group22.add(file100Tbutton);
//  panel21.add(file100Tbutton);  // currently disabled: too big

    file1Pbutton = new JRadioButton("1 PB"); // petabyte (10**15)
    if (buttonFont != null) file1Pbutton.setFont(buttonFont);
    group22.add(file1Pbutton);
//  panel21.add(file1Pbutton);    // currently disabled: too big

    file10Pbutton = new JRadioButton("10 PB");
    if (buttonFont != null) file10Pbutton.setFont(buttonFont);
    group22.add(file10Pbutton);
//  panel21.add(file10Pbutton);   // currently disabled: too big

    file100Pbutton = new JRadioButton("100 PB");
    if (buttonFont != null) file100Pbutton.setFont(buttonFont);
    group22.add(file100Pbutton);
//  panel21.add(file100Pbutton);  // currently disabled: too big

    file1Ebutton = new JRadioButton("1 EB"); // exabyte (10**18)
    if (buttonFont != null) file1Ebutton.setFont(buttonFont);
    group22.add(file1Ebutton);
//  panel21.add(file1Ebutton);    // currently disabled: too big

    fileMaximumButton = new JRadioButton("maximum");
    if (buttonFont != null) fileMaximumButton.setFont(buttonFont);
    group22.add(fileMaximumButton);
    panel21.add(fileMaximumButton);

    panel01.add(panel21);
    panel01.add(Box.createVerticalStrut(10)); // space between panels

    /* Miscellaneous options.  Maybe these should be hidden from the user. */

    JPanel panel31 = new JPanel();
    panel31.setBorder(BorderFactory.createTitledBorder(null, " Options ",
      TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION,
      buttonFont));
    panel31.setLayout(new FlowLayout(FlowLayout.CENTER, 20, 0));

    JPanel panel32 = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    ignoreCheckbox = new JCheckBox("ignore first and last ", true);
    if (buttonFont != null) ignoreCheckbox.setFont(buttonFont);
    ignoreCheckbox.setToolTipText("Delay before calculating averages.");
    panel32.add(ignoreCheckbox);
    ignoreDialog = new JComboBox(IGNORE_CHOICES);
    ignoreDialog.setEditable(false); // user must select one of our choices
    if (buttonFont != null) ignoreDialog.setFont(buttonFont);
    ignoreDialog.setSelectedItem(IGNORE_DEFAULT);
    ignoreDialog.addActionListener(action); // do last so don't fire early
    panel32.add(ignoreDialog);
    if (mswinFlag) panel32.add(Box.createHorizontalStrut(4));
                                  // adjust Sun/Oracle Java 1.4 to 7 on Windows
    JLabel label33 = new JLabel(" seconds");
    if (buttonFont != null) label33.setFont(buttonFont);
    label33.setToolTipText("Delay before calculating averages.");
    panel32.add(label33);
    panel31.add(panel32);

    promptCheckbox = new JCheckBox("prompt before reading", false);
    if (buttonFont != null) promptCheckbox.setFont(buttonFont);
    promptCheckbox.setToolTipText("Select for removable media.");
    panel31.add(promptCheckbox);

    panel01.add(panel31);
    panel01.add(Box.createVerticalStrut(20)); // space between panels

    /* Create a horizontal panel for the progress bar and status text. */

    progressBar = new JProgressBar(0, 100);
    progressBar.setBorderPainted(false);
    if (buttonFont != null) progressBar.setFont(buttonFont);
    progressBar.setString(
      " 999,999,999,999,999,999 bytes of 999,999,999,999,999,999 or 999.9% ");
    progressBar.setStringPainted(true);

    panel01.add(progressBar);
    panel01.add(Box.createVerticalStrut(20)); // space between panels

    /* Create a horizontal panel for the action buttons. */

    JPanel panel51 = new JPanel();
    panel51.setLayout(new BoxLayout(panel51, BoxLayout.X_AXIS));

    driveFolderButton = new JButton("Drive Folder...");
    driveFolderButton.addActionListener(action);
    if (buttonFont != null) driveFolderButton.setFont(buttonFont);
    driveFolderButton.setMnemonic(KeyEvent.VK_D);
    driveFolderButton.setToolTipText("Select folder on disk drive.");
    panel51.add(driveFolderButton);

    panel51.add(Box.createHorizontalGlue()); // space between buttons

    startButton = new JButton("Start");
    startButton.addActionListener(action);
    startButton.setEnabled(false);
    if (buttonFont != null) startButton.setFont(buttonFont);
    startButton.setMnemonic(KeyEvent.VK_S);
    startButton.setToolTipText("Start writing then reading.");
    panel51.add(startButton);

    panel51.add(Box.createHorizontalGlue()); // space between buttons

    cancelButton = new JButton("Cancel");
    cancelButton.addActionListener(action);
    cancelButton.setEnabled(false);
    if (buttonFont != null) cancelButton.setFont(buttonFont);
    cancelButton.setMnemonic(KeyEvent.VK_C);
    cancelButton.setToolTipText("Stop reading or writing.");
    panel51.add(cancelButton);

    panel51.add(Box.createHorizontalGlue()); // space between buttons

    exitButton = new JButton("Exit");
    exitButton.addActionListener(action);
    if (buttonFont != null) exitButton.setFont(buttonFont);
    exitButton.setMnemonic(KeyEvent.VK_X);
    exitButton.setToolTipText("Close this program.");
    panel51.add(exitButton);

    panel01.add(panel51);
    panel01.add(Box.createVerticalStrut(25)); // space between panels

    /* Create a horizontal panel for the read and write speeds.  Align the
    numbers with BorderLayout.NORTH instead of BorderLayout.CENTER to match
    what FlowLayout does inside the titled borders above when our application
    window expands. */

    JPanel panel61 = new JPanel(new GridLayout(1, 2, 20, 0));

    JPanel panel62 = new JPanel();
    panel62.setBorder(BorderFactory.createTitledBorder(null, " Read Speed ",
      TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION,
      buttonFont));
    panel62.setLayout(new BorderLayout(0, 0));
    panel62.setToolTipText("Current or final average read speed.");
    readSpeedText = new JTextField("9,999.9 MB/s", 9); // minimum of 8 columns
    readSpeedText.setBorder(BorderFactory.createEmptyBorder(5, 10, 10, 10));
    readSpeedText.setEditable(false); // user can't change this text area
    if (speedFont != null) readSpeedText.setFont(speedFont);
    readSpeedText.setHorizontalAlignment(JTextField.CENTER);
    readSpeedText.setOpaque(false);
    panel62.add(readSpeedText, BorderLayout.NORTH);
    panel61.add(panel62);

    JPanel panel63 = new JPanel();
    panel63.setBorder(BorderFactory.createTitledBorder(null, " Write Speed ",
      TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION,
      buttonFont));
    panel63.setLayout(new BorderLayout(0, 0));
    panel63.setToolTipText("Current or final average write speed.");
    writeSpeedText = new JTextField("9,999.9 MB/s", 9); // minimum of 8 columns
    writeSpeedText.setBorder(BorderFactory.createEmptyBorder(5, 10, 10, 10));
    writeSpeedText.setEditable(false); // user can't change this text area
    if (speedFont != null) writeSpeedText.setFont(speedFont);
    writeSpeedText.setHorizontalAlignment(JTextField.CENTER);
    writeSpeedText.setOpaque(false);
    panel63.add(writeSpeedText, BorderLayout.NORTH);
    panel61.add(panel63);

    panel01.add(panel61);

    /* Nothing in our layout needs to expand if the window size gets bigger, so
    center everything horizontally and vertically.  The glue will stretch. */

    Box panel91 = Box.createHorizontalBox(); // basic horizontal box
    panel91.add(Box.createGlue()); // stretch to the left
    panel91.add(panel01);
    panel91.add(Box.createGlue()); // stretch to the right

    Box panel92 = Box.createVerticalBox(); // basic vertical box
    panel92.add(Box.createGlue()); // stretch to the top
    panel92.add(panel91);
    panel92.add(Box.createGlue()); // stretch to the bottom

    /* Create the main window frame for this application.  We use a border
    layout to add margins around the central area. */

    mainFrame = new JFrame(PROGRAM_TITLE);
    Container panel93 = mainFrame.getContentPane(); // where content meets frame
    panel93.setLayout(new BorderLayout(0, 0));
    panel93.add(Box.createVerticalStrut(25), BorderLayout.NORTH);
    panel93.add(Box.createHorizontalStrut(20), BorderLayout.WEST);
    panel93.add(panel92, BorderLayout.CENTER); // initial panel
    panel93.add(Box.createHorizontalStrut(20), BorderLayout.EAST);
    panel93.add(Box.createVerticalStrut(20), BorderLayout.SOUTH);

    mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    mainFrame.setLocation(windowLeft, windowTop); // normal top-left corner
    if ((windowHeight < MIN_FRAME) || (windowWidth < MIN_FRAME))
      mainFrame.pack();           // do component layout with minimum size
    else                          // the user has given us a window size
      mainFrame.setSize(windowWidth, windowHeight); // size of normal window
    if (maximizeFlag) mainFrame.setExtendedState(JFrame.MAXIMIZED_BOTH);
    mainFrame.validate();         // recheck application window layout
    driveFolderButton.requestFocusInWindow(); // give keyboard focus to button
    mainFrame.setVisible(true);   // and then show application window

    /* Let the graphical interface run the application now. */

    progressBar.setString(NO_DRIVE_TEXT); // clear placeholder text
    readSpeedText.setText(NO_SPEED_TEXT);
    writeSpeedText.setText(NO_SPEED_TEXT);

  } // end of main() method

// ------------------------------------------------------------------------- //

/*
  checkDriveFolder() method

  If a writeable folder has been selected, then enable the "Start" button.
  This method is overly protective because it is possible to select a good
  folder, then delete the folder, before clicking the "Start" button.
*/
  static void checkDriveFolder()
  {
    if (driveSelection == null)   // if there is no drive selection
    {
//    driveSelection = null;      // cancel any previous selection
      progressBar.setString(NO_DRIVE_TEXT); // repeat generic message
      progressBar.setValue(0);    // and clear any previous status value
      startButton.setEnabled(false); // can't start with this
    }
    else if (driveSelection.exists() == false) // if it doesn't exist
    {
      driveSelection = null;      // cancel any previous selection
      progressBar.setString("Selected drive folder does not exist.");
      progressBar.setValue(0);    // and clear any previous status value
      startButton.setEnabled(false); // can't start with this
    }
    else if (driveSelection.isDirectory() == false) // if it's not a folder
    {
      driveSelection = null;      // cancel any previous selection
      progressBar.setString("Selected object is not a directory or folder.");
      progressBar.setValue(0);    // and clear any previous status value
      startButton.setEnabled(false); // can't start with this
    }
    else if (driveSelection.canWrite() == false) // if we can't write to it
    {
      driveSelection = null;      // cancel any previous selection
      progressBar.setString("Can't write to selected directory or folder.");
      progressBar.setValue(0);    // and clear any previous status value
      startButton.setEnabled(false); // can't start with this
    }
    else                          // this folder should be good for writing
    {
      /* Do nothing here.  Our caller will have additional code. */
    }
  } // end of checkDriveFolder() method


/*
  createFilename() method

  Common routine to create a temporary file name given a file number, so that
  all methods create the same file names in the old MS-DOS 8.3 format.
*/
  static String createFilename(int number)
  {
    String digits = String.valueOf(number); // convert integer to characters
    return("ERASE000".substring(0, (8 - digits.length())) + digits + ".DAT");
  }


/*
  formatSpeed() method

  Given a transfer rate or speed in bytes per second, format a string with the
  speed in kilobytes per second, megabytes, gigabytes, or terabytes (whichever
  is the most expressive).
*/
  static String formatSpeed(double speed)
  {
    double units = speed;         // start with bytes per second
    String suffix = " B/s";       // matching string with those units
    if (units > 1999.4) { units = units / 1024.0; suffix = " KB/s"; }
    if (units > 1999.4) { units = units / 1024.0; suffix = " MB/s"; }
    if (units > 1999.4) { units = units / 1024.0; suffix = " GB/s"; }
    if (units > 1999.4) { units = units / 1024.0; suffix = " TB/s"; }
    if (units > 1999.4) { units = units / 1024.0; suffix = " PB/s"; }
    return(formatPointOne.format(units) + suffix); // scaled into units
  }


/*
  queueAdd() method

  Add a new byte count and clock time to the end of our queue of pending data.
  This is really a circular buffer, because that's faster and doesn't create
  numerous Long objects like Vector or other classes would.
*/
  static void queueAdd(long byteCount, long clockTime)
  {
    int i;                        // index variable

    if (queueItemCount < QUEUE_SIZE) // stop if too many items in queue
    {
      i = (queueFirstIndex + queueItemCount) % QUEUE_SIZE; // next index
      queueDataBytes[i] = byteCount; // save number of bytes
      queueDataClock[i] = clockTime; // save clock time in milliseconds
      queueItemCount ++;          // one more item in pending clock data
    }
    else
      throw new IndexOutOfBoundsException("pending clock data is full");
  }


/*
  queueClear() method

  Clear or initialize our queue of pending data about byte counts and clock
  times.
*/
  static void queueClear()
  {
    if (queueDataBytes == null) queueDataBytes = new long[QUEUE_SIZE];
    if (queueDataClock == null) queueDataClock = new long[QUEUE_SIZE];
    queueFirstIndex = 0;          // first item goes at index zero
    queueItemCount = 0;           // there is nothing in the queue
  }


/*
  queueDelete() method

  Delete the first byte count and clock time from our queue of pending data.
*/
  static void queueDelete()
  {
    if (queueItemCount > 0)       // must be something before we can delete
    {
      queueFirstIndex = (queueFirstIndex + 1) % QUEUE_SIZE; // next index
      queueItemCount --;          // one less item in pending clock data
    }
    else
      throw new IndexOutOfBoundsException("pending clock data is empty");
  }


/*
  showHelp() method

  Show the help summary.  This is a UNIX standard and is expected for all
  console applications, even very simple ones.
*/
  static void showHelp()
  {
    System.err.println();
    System.err.println(PROGRAM_TITLE);
    System.err.println();
    System.err.println("This is a graphical application.  You may give options on the command line:");
    System.err.println();
    System.err.println("  -? = -help = show summary of command-line syntax");
    System.err.println("  -u# = font size for buttons, dialogs, etc; default is local system;");
    System.err.println("      example: -u16");
    System.err.println("  -w(#,#,#,#) = normal window position: left, top, width, height;");
    System.err.println("      example: -w(50,50,700,500)");
    System.err.println("  -x = maximize application window; default is normal window");
    System.err.println();
    System.err.println(COPYRIGHT_NOTICE);
//  System.err.println();

  } // end of showHelp() method


/*
  startErase() method

  Erase the disk drive.  This method should be called from a secondary thread,
  not from the main thread that runs the GUI.

  The value of <bufferSize> plus the value of <maxDataBytes> must be less than
  Long.MAX_VALUE or else <bytesAllFiles> can overflow, go negative, and create
  an infinite loop of writing.  There are currently no storage devices of such
  capacity except for a null device.  If this is a concern, then put the check
  (bytesAllFiles >= 0) and the condition (bytesAllFiles < maxDataBytes) below.
*/
  static void startErase()
  {
    byte[] buffer;                // data buffer for reading, writing files
    int bufferSize;               // number of bytes in our data buffer
    long bytesThisFile;           // number of bytes in current file
    int fileNumber;               // current file number in <totalFiles>
    long finalBytes;              // number of bytes between start/stop delays
    long finalClock;              // elapsed time between start/stop delays
    File fp;                      // File object to write, read, or delete
    int i;                        // index variable
    FileInputStream inp;          // byte input stream for reading one file
    FileOutputStream out;         // byte output stream for writing one file
    int totalFilesCreated;        // total number of temporary files created

    /* Check that we still have a valid drive folder for writing files. */

    checkDriveFolder();           // get someone else to check the folder
    if (driveSelection == null)   // if there was something wrong with folder
      return;                     // our "Start" button now disabled by check

    /* Create a data buffer of the correct size, as chosen by the user.  Small
    buffer sizes can cause excessive system overhead, and must be big enough to
    guarantee an error if the disk is full: some file systems store very small
    files inside the directory structure, around 728 bytes or less for NTFS. */

    if (buffer1Kbutton.isSelected()) { bufferSize = 0x400; } // 1 KB
    else if (buffer2Kbutton.isSelected()) { bufferSize = 0x800; } // 2 KB
    else if (buffer4Kbutton.isSelected()) { bufferSize = 0x1000; } // 4 KB
    else if (buffer8Kbutton.isSelected()) { bufferSize = 0x2000; } // 8 KB
    else if (buffer16Kbutton.isSelected()) { bufferSize = 0x4000; } // 16 KB
    else if (buffer32Kbutton.isSelected()) { bufferSize = 0x8000; } // 32 KB
    else if (buffer64Kbutton.isSelected()) { bufferSize = 0x10000; } // 64 KB
    else if (buffer128Kbutton.isSelected()) { bufferSize = 0x20000; } // 128 KB
    else if (buffer256Kbutton.isSelected()) { bufferSize = 0x40000; } // 256 KB
    else if (buffer512Kbutton.isSelected()) { bufferSize = 0x80000; } // 512 KB
    else if (buffer1Mbutton.isSelected()) { bufferSize = 0x100000; } // 1 MB
    else if (buffer2Mbutton.isSelected()) { bufferSize = 0x200000; } // 2 MB
    else if (buffer4Mbutton.isSelected()) { bufferSize = 0x400000; } // 4 MB
    else if (buffer8Mbutton.isSelected()) { bufferSize = 0x800000; } // 8 MB
    else if (buffer16Mbutton.isSelected()) { bufferSize = 0x1000000; } // 16 MB
    else if (buffer32Mbutton.isSelected()) { bufferSize = 0x2000000; } // 32 MB
    else if (buffer64Mbutton.isSelected()) { bufferSize = 0x4000000; } // 64 MB
    else if (buffer128Mbutton.isSelected()) { bufferSize = 0x8000000; } // 128 MB
    else if (buffer256Mbutton.isSelected()) { bufferSize = 0x10000000; } // 256 MB
    else if (buffer512Mbutton.isSelected()) { bufferSize = 0x20000000; } // 512 MB
    else { bufferSize = 0x40000; } // 256 KB (again)

    try { buffer = new byte[bufferSize]; } // allocate the data buffer
    catch (OutOfMemoryError oome) // some size options can be too big
    {
      JOptionPane.showMessageDialog(mainFrame,
        ("Not enough memory for a data buffer of "
        + formatComma.format(bufferSize)
        + " bytes.\nChoose a smaller buffer or increase the Java heap size with\nthe -Xmx option on the command line."));
      return;                     // act like nothing ever happened
    }
    for (i = 0; i < bufferSize; i ++)
      buffer[i] = 0x00;           // fill buffer with binary zeros

    /* Find the maximum number of bytes to write, a total for all temporary
    files.  Go back in history for smaller maximums.  Java 1.4.2 was first
    released in June 2003, if you run Windows 98 and need to only partially
    test 1.44 MB floppy disks! */

    if (file1Mbutton.isSelected()) { maxDataBytes = 0x100000L; } // 1 MB
    else if (file10Mbutton.isSelected()) { maxDataBytes = 0xA00000L; } // 10 MB
    else if (file100Mbutton.isSelected()) { maxDataBytes = 0x6400000L; } // 100 MB
    else if (file1Gbutton.isSelected()) { maxDataBytes = 0x40000000L; } // 1 GB
    else if (file10Gbutton.isSelected()) { maxDataBytes = 0x280000000L; } // 10 GB
    else if (file100Gbutton.isSelected()) { maxDataBytes = 0x1900000000L; } // 100 GB
    else if (file1Tbutton.isSelected()) { maxDataBytes = 0x10000000000L; } // 1 TB
    else if (file10Tbutton.isSelected()) { maxDataBytes = 0xA0000000000L; } // 10 TB
    else if (file100Tbutton.isSelected()) { maxDataBytes = 0x640000000000L; } // 100 TB
    else if (file1Pbutton.isSelected()) { maxDataBytes = 0x4000000000000L; } // 1 PB
    else if (file10Pbutton.isSelected()) { maxDataBytes = 0x28000000000000L; } // 10 PB
    else if (file100Pbutton.isSelected()) { maxDataBytes = 0x190000000000000L; } // 100 PB
    else if (file1Ebutton.isSelected()) { maxDataBytes = 0x1000000000000000L; } // 1 EB
    else { maxDataBytes = MAX_FILE_SIZE; } // safe positive 64-bit integer

    /* Disable the "Start" button until we are done, and enable a "Cancel"
    button in case our secondary thread runs for a long time and the user
    panics. */

    cancelButton.setEnabled(true); // enable button to cancel this processing
    cancelButton.requestFocusInWindow(); // give keyboard focus to button
    cancelFlag = false;           // but don't cancel unless user complains
    deleteFlag = true;            // we should delete our temporary files
    driveFolderButton.setEnabled(false); // disable "Drive Folder" button
    eraseState = ERASE_NONE;      // we are not reading or writing data
    progressBar.setString("");    // empty string, not built-in percent
    progressBar.setValue(0);      // and clear any previous status value
    readSpeedText.setText(NO_SPEED_TEXT); // clear previous read speed
    startButton.setEnabled(false); // suspend "Start" until we are done
    totalBytesWritten = 0;        // no bytes written yet
    writeSpeedText.setText(NO_SPEED_TEXT); // clear previous write speed

    /* Create as many temporary files as necessary to get the total number of
    bytes selected by the user. */

    bytesAllFiles = 0;            // no bytes written yet
    fileNumber = 0;               // no files created yet
    ignoreDelayMillis = ignoreCheckbox.isSelected() ? (1000
      * Integer.parseInt((String) ignoreDialog.getSelectedItem())) : 0;
    ignoreStartBytes = ignoreStopBytes = -1; // no data yet after delays
    ignoreStartClock = ignoreStopClock = startTime = System.currentTimeMillis();
                                  // starting clock time in milliseconds
    userBytesPrev = 0;            // no bytes reported to user yet
    userBytesRate = -1.0;         // no current or previous bytes per second

    eraseState = ERASE_WRITE;     // we are now writing data
    queueClear();                 // clear queue of pending clock data
    updateProgressBar();          // force the progress bar to update
    statusTimer.start();          // start updating the status message
    while ((cancelFlag == false)  // while the user hasn't cancelled us
      && (fileNumber < MAX_FILE_COUNT) // and we don't have too many files
      && (bytesAllFiles < maxDataBytes)) // and there are more bytes to write
    {
      /* Create one file and fill it with zeros.  We don't report errors to the
      user, because we assume that all errors mean "disk is full" or a file has
      reached the maximum size. */

      bytesThisFile = 0;          // no bytes written to this file yet
      fileNumber ++;              // one more temporary file will be created
      fp = new File(driveSelection, createFilename(fileNumber)); // from name
      try { out = new FileOutputStream(fp); } // we do our own buffering
      catch (FileNotFoundException fnfe) // the only documented exception
      {
        fileNumber --;            // we failed, so don't count this file
        break;                    // exit early from outer <while> loop
      }
      while ((cancelFlag == false) && (bytesAllFiles < maxDataBytes))
      {
        try { out.write(buffer); } // write one buffer full of constant data
        catch (IOException ioe)   // assume all errors are "disk may be full"
        {
          break;                  // exit early from inner <while> loop
        }
        bytesAllFiles += bufferSize; // add to total bytes done all files
        bytesThisFile += bufferSize; // add to bytes done for current file
      }
      try { out.close(); } catch (IOException ioe) { /* ignore errors */ }
      if (bytesThisFile < bufferSize) // small files may mean disk is full
        break;                    // exit early from outer <while> loop
    }
    eraseState = ERASE_NONE;      // we are not reading or writing data
    statusTimer.stop();           // stop updating status message by timer
    totalFilesCreated = fileNumber; // remember total number of files created
    if (cancelFlag == false)      // only if the user hasn't cancelled us
    {
      updateProgressBar();        // force the progress bar to update
      totalBytesWritten = bytesAllFiles; // remember total bytes written
    }

    /* Calculate the final average write speed. */

    if (cancelFlag == false)      // only if the user hasn't cancelled us
    {
      finalBytes = ignoreStopBytes - ignoreStartBytes; // number of data bytes
      finalClock = ignoreStopClock - ignoreStartClock; // elapsed clock time
      if ((finalBytes > 0) && (finalClock > 0)) // don't divide by zero
        writeSpeedText.setText(formatSpeed((double) finalBytes * 1000.0
          / (double) finalClock));
      else
        writeSpeedText.setText(TOO_FAST_TEXT); // can't report accurate speed
    }

    /* Java has no standard way of invalidating disk caches in hardware or the
    underlying operating system.  If the amount of data written is smaller than
    the physical memory (RAM) on the computer, data that we read may be fetched
    from the cache and not from the drive.  For removable media, one possible
    solution is a pop-up dialog that asks the user to remove (eject) and then
    reinsert the media. */

    if ((cancelFlag == false) && promptCheckbox.isSelected())
    {
      JOptionPane.showMessageDialog(mainFrame,
        ("If your drive is on removable media, then:\n"
        + "1. Remove (eject) the drive normally;\n"
        + "2. Reinsert the drive; and\n"
        + "3. Click the OK button here."));
    }

    /* Read the files that we just created.  All errors are unexpected and will
    be reported in a pop-up dialog box. */

    if (cancelFlag == false)      // only if the user hasn't cancelled us
    {
      bytesAllFiles = 0;          // no bytes read yet
      fileNumber = 1;             // start with first file we created
      ignoreDelayMillis = ignoreCheckbox.isSelected() ? (1000
        * Integer.parseInt((String) ignoreDialog.getSelectedItem())) : 0;
      ignoreStartBytes = ignoreStopBytes = -1; // no data yet after delays
      ignoreStartClock = ignoreStopClock = startTime = System.currentTimeMillis();
                                  // starting clock time in milliseconds
      userBytesPrev = 0;          // no bytes reported to user yet
      userBytesRate = -1.0;       // no current or previous bytes per second

      eraseState = ERASE_READ;    // we are now reading data
      queueClear();               // clear queue of pending clock data
      updateProgressBar();        // force the progress bar to update
      statusTimer.start();        // start updating the status message
      while ((cancelFlag == false) // while the user hasn't cancelled us
        &&  (fileNumber <= totalFilesCreated)) // and there are more files
      {
//      bytesThisFile = 0;        // no bytes read from this file yet
        fp = new File(driveSelection, createFilename(fileNumber));
        try                       // general try-catch for all read errors
        {
          inp = new FileInputStream(fp); // we do our own buffering
          while ((cancelFlag == false) && ((i = inp.read(buffer)) > 0))
          {
            bytesAllFiles += i;   // add to total bytes done all files
//          bytesThisFile += i;   // add to bytes done for current file
          }
          inp.close();            // close the input file
        }
        catch (IOException ioe)   // all errors are bad news when reading
        {
          JOptionPane.showMessageDialog(mainFrame, ("Read error on file "
            + fp.getName()));     // not very helpful without ioe.getMessage()
          break;                  // exit early from outer <while> loop
        }
        fileNumber ++;            // now do the next temporary file
      }
      statusTimer.stop();         // stop updating status message by timer
    }
    eraseState = ERASE_NONE;      // we are not reading or writing data
    if (cancelFlag == false)      // only if the user hasn't cancelled us
    {
      updateProgressBar();        // force the progress bar to update
    }

    /* Calculate the final average read speed. */

    if (cancelFlag == false)      // only if the user hasn't cancelled us
    {
      finalBytes = ignoreStopBytes - ignoreStartBytes; // number of data bytes
      finalClock = ignoreStopClock - ignoreStartClock; // elapsed clock time
      if ((finalBytes > 0) && (finalClock > 0)) // don't divide by zero
        readSpeedText.setText(formatSpeed((double) finalBytes * 1000.0
          / (double) finalClock));
      else
        readSpeedText.setText(TOO_FAST_TEXT); // can't report accurate speed
    }

    /* Delete our temporary files.  We ignore most errors here. */

    if (deleteFlag)               // should we delete our temporary files?
    {
      for (i = 1; i <= totalFilesCreated; i ++) // for each file we created
      {
        fp = new File(driveSelection, createFilename(i)); // from file name
        fp.delete();              // try to delete this file, ignore errors
      }
    }

    /* We are done.  Turn off the "Cancel" button and allow the user to click
    the "Start" button again. */

    cancelButton.setEnabled(false); // disable "Cancel" button
    driveFolderButton.setEnabled(true); // enable "Drive Folder" button
    startButton.setEnabled(true); // enable "Start" button
    startButton.requestFocusInWindow(); // give keyboard focus to button
    mainFrame.repaint();          // sometimes gets behind after all updating

  } // end of startErase() method


/*
  updateProgressBar() method

  Update the progress bar (status timer) at scheduled clock ticks, so that the
  text doesn't change too quickly, and the progress bar doesn't advance too
  smoothly.  Since this method runs in a separate timer thread (independent),
  we are careful to work with consistent local copies of global variables, and
  to set text fields as single, complete strings.
*/
  static void updateProgressBar()
  {
    StringBuffer buffer;          // for creating complete text strings
    long clock = System.currentTimeMillis(); // system time in milliseconds
    long done = bytesAllFiles;    // get local copy so it doesn't change
    double percent;               // for calculating percent complete
    double rate;                  // current (most recent) bytes per second
    String speed;                 // current data rate for reading or writing
    long total = totalBytesWritten; // get local copy so it doesn't change

    /* Basic information for the progress bar and status text.  This is mostly
    independent of reading and writing, except that we can infer the state. */

    buffer = new StringBuffer();  // faster than String for appends
    buffer.append(formatComma.format(done));
    if ((total > 0) || ((total = maxDataBytes) < MAX_FILE_SIZE))
    {                             // if reading, or writing with a limit
      buffer.append(" bytes of ");
      buffer.append(formatComma.format(total));
      if (done <= total)          // can we calculate percent done?
      {
        percent = (100.0 * (double) done) / (double) total;
        progressBar.setValue((int) percent); // set size of progress bar
        buffer.append(" or ");
        buffer.append(formatPointOne.format(percent));
        buffer.append("%");
      }
    }
    else                          // else writing without a limit
    {
      buffer.append(" bytes written, unknown total");
    }
    progressBar.setString(buffer.toString()); // set text for progress bar

    /* Update the current speed.  A weighted average with a short delay shows
    smoother values.  Since the numbers shown here are transient, assume that
    TIMER_DELAY is a good measure of time between calls to this method.  Don't
    calculate an offset from the system clock, which would be slower and might
    allow for division by zero. */

    rate = (double) (done - userBytesPrev) * 1000.0 / TIMER_DELAY;
    if (userBytesRate < 0.0)      // were there previous bytes per second?
      userBytesRate = rate;       // no, fix calculation with current rate
    speed = formatSpeed((rate * 0.7) + (userBytesRate * 0.3));
                                  // scale into nice units per second
    if (eraseState == ERASE_READ) // if we are actively reading
      readSpeedText.setText(speed);
    else if (eraseState == ERASE_WRITE) // if we are actively writing
      writeSpeedText.setText(speed);
    userBytesPrev = done;         // remember previously reported amount
    userBytesRate = rate;         // remember current bytes per second

    /* Don't count data bytes during the first and last few seconds of a read/
    write pass, to avoid our final averages being skewed by some common forms
    of disk caching. */

    if ((clock - startTime) < ignoreDelayMillis)
    {
      /* Do nothing during a starting delay before we collect information. */
    }
    else
    {
      /* This clock event is after our starting delay.  Save the first as our
      starting byte count and time.  Add each to a list of pending clock data,
      then peel off older information that may now be a valid stopping time. */

      if (ignoreStartBytes < 0)   // first information after starting delay?
      {
        ignoreStartBytes = done;  // ignore all data bytes before now
        ignoreStartClock = clock; // remember when we started good data
      }
      queueAdd(done, clock);      // add bytes, time to pending clock data
      while ((queueItemCount > 0) // look at older items in pending clock data
        && ((clock - queueDataClock[queueFirstIndex]) >= ignoreDelayMillis))
      {
        ignoreStopBytes = queueDataBytes[queueFirstIndex]; // new byte count
        ignoreStopClock = queueDataClock[queueFirstIndex]; // new clock time
        queueDelete();            // delete this item from pending clock data
      }
    }
  } // end of updateProgressBar() method


/*
  userButton() method

  This method is called by our action listener actionPerformed() to process
  buttons, in the context of the main DriveSpeed1 class.
*/
  static void userButton(ActionEvent event)
  {
    Object source = event.getSource(); // where the event came from
    if (source == cancelButton)   // "Cancel" button
    {
      int reply = JOptionPane.showConfirmDialog(mainFrame,
        "Cancel button clicked during test.\nDelete temporary files first?");
      if (reply == JOptionPane.CANCEL_OPTION)
        return;                   // do nothing and ignore Cancel button
      else                        // otherwise the answer was "Yes" or "No"
        deleteFlag = (reply == JOptionPane.YES_OPTION);
      cancelButton.setEnabled(false); // don't allow multiple cancels
      cancelFlag = true;          // tell other threads that all work stops now
    }
    else if (source == driveFolderButton) // "Drive Folder" button
    {
      fileChooser.resetChoosableFileFilters(); // remove any existing filters
      fileChooser.setDialogTitle("Select Writeable Drive Folder...");
      fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
      fileChooser.setMultiSelectionEnabled(false); // allow only one folder
      if (fileChooser.showOpenDialog(mainFrame) == JFileChooser.APPROVE_OPTION)
        driveSelection = fileChooser.getSelectedFile(); // correct Java object
      checkDriveFolder();         // get someone else to check the folder
      if (driveSelection != null) // if the folder is acceptable
      {
        progressBar.setString("Click <Start> button when ready to begin.");
        progressBar.setValue(0);  // and clear any previous status value
        startButton.setEnabled(true); // we are good to go
      }
    }
    else if (source == exitButton) // "Exit" button
    {
      System.exit(0);             // always exit with zero status from GUI
    }
    else if (source == ignoreDialog) // number of seconds to ignore
    {
      ignoreCheckbox.setSelected(true); // choosing a number forces selection
    }
    else if (source == startButton) // "Start" button
    {
      Thread th = new Thread(new DriveSpeed1User(), "eraseThread");
      th.setPriority(Thread.MIN_PRIORITY); // use lowest priority in Java VM
      th.start();                 // now run as separate thread to erase disk
    }
    else if (source == statusTimer) // update timer for status message text
    {
      updateProgressBar();        // force the progress bar to update
    }
    else                          // fault in program logic, not by user
    {
      System.err.println("Error in userButton(): unknown ActionEvent: "
        + event);                 // should never happen, so write on console
    }
  } // end of userButton() method

} // end of DriveSpeed1 class

// ------------------------------------------------------------------------- //

/*
  DriveSpeed1User class

  This class listens to input from the user and passes back event parameters to
  a static method in the main class.
*/

class DriveSpeed1User implements ActionListener, Runnable
{
  /* empty constructor */

  public DriveSpeed1User() { }

  /* button listener, dialog boxes, etc */

  public void actionPerformed(ActionEvent event)
  {
    DriveSpeed1.userButton(event);
  }

  /* separate heavy-duty processing thread */

  public void run() { DriveSpeed1.startErase(); }

} // end of DriveSpeed1User class

/* Copyright (c) 2016 by Keith Fenske.  Apache License or GNU GPL. */
