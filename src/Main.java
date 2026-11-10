/*
 * =====================================================================
 *  MediRoute - Emergency Response System  (single-file Java Swing app)
 * =====================================================================
 *  GUI TOOLKIT USED: Java SWING (javax.swing + java.awt).
 *    Why Swing?  It ships with the JDK (no extra libraries), it is
 *    lightweight, supports custom painting (paintComponent) for the
 *    charts / map / animations, and CardLayout makes multi-page apps easy.
 *    (AWT is only used underneath for Graphics2D, colors, fonts, events.)
 *
 *  THE 4 OOP PRINCIPLES  (search these words in the code):
 *    1. INHERITANCE   - Main extends JFrame; every page extends AppPage;
 *                       HoverPanel / RoundedButton / charts extend Swing classes.
 *    2. ENCAPSULATION - EmergencyRequest, AmbulanceUnit, HospitalData and
 *                       Main's lists keep private fields + public getters/setters.
 *    3. ABSTRACTION   - AppPage is abstract and declares abstract refreshData();
 *                       each page only implements what it needs.
 *    4. POLYMORPHISM  - Main calls refreshData() on any page and each page runs
 *                       its own version; paintComponent()/renderers are overridden.
 *
 *  FILE HANDLING   : class FileManager (near the bottom, before FeatureCard).
 *                    - saveRequest()  appends a dispatch to MediRoute_history.txt
 *                    - saveAll()      rewrites the file when a status changes
 *                    - loadAll()      reads the file at start-up so the History
 *                                     page still shows old dispatches.
 *                    The file is stored in the user's home folder.
 *
 *  EXCEPTION HANDLING (try / catch / finally / try-with-resources):
 *    - FileManager (IOException, NumberFormatException, ParseException)
 *    - Main.resolve() and the image loaders in HomePage / AboutPage / LoginPage
 *    - EmergencyPage form validation (IllegalArgumentException)
 *    - SirenPlayer (audio errors are caught so the app never crashes)
 *
 *  HISTORY PAGE (this version):
 *    - Table is always visible (the page scrolls instead of squeezing the table).
 *    - Click a row            -> shows its Patient Care Report.
 *    - Click a donut slice    -> filters the table by that call type.
 *    - Click a month dot      -> filters the table by that month.
 *    - Click the 3 overview cards -> reset / Critical only / Completed only.
 *    - Charts count only non-canceled dispatches, so a new dispatch makes the
 *      graph go UP and a canceled dispatch makes it go DOWN (animated).
 *    - The window opens maximized and can be resized freely.
 * =====================================================================
 */
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;
import javax.imageio.ImageIO;
import java.awt.geom.Arc2D;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import javax.swing.table.*;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Line2D;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.PriorityQueue;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.KeyEvent;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;


/*
 * INHERITANCE: Main extends JFrame, so it inherits all window behaviour
 * (title, size, close button, layout) from Swing and only adds
 * MediRoute-specific logic on top.
 *
 * ENCAPSULATION: the lists of requests, ambulances and hospitals are
 * private. Other classes can only reach them through public methods such as
 * getRequests(), addRequest(), findNearestFreeAmbulance() and
 * findNearestHospitalWithBed().
 */
public class Main extends JFrame {

    // ===== COLORS =====
    static final Color BG = new Color(10, 14, 18);
    static final Color PANEL = new Color(20, 25, 30);
    static final Color RED = new Color(245, 45, 45);
    static final Color TEXT = new Color(245, 245, 245);
    static final Color MUTED = new Color(160, 165, 170);
    static final Color GREEN = new Color(70, 210, 125);

    // ===== PAGE MANAGEMENT =====
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel pages = new JPanel(cardLayout);

    // POLYMORPHISM: every page below is stored by its own type, but all of
    // them are AppPage objects that respond to refreshData() in their own way.
    private final HomePage homePage;
    private final EmergencyPage emergencyPage;
    private final TrackingPage trackingPage;
    private final BedsPage bedsPage;
    private final HospitalsPage hospitalsPage;
    private final HistoryPage historyPage;
    private final ServicesPage servicesPage;
    private final AboutPage aboutPage;
    private final HeaderBar header;
    private final FooterBar footer;


    // ENCAPSULATION: private data lists, accessed only through methods.
    private final List<EmergencyRequest> requests = new ArrayList<EmergencyRequest>();
    private final List<AmbulanceUnit> ambulances = new ArrayList<AmbulanceUnit>();
    private final List<HospitalData> hospitals = new ArrayList<HospitalData>();
    private int requestCounter = 100;
    // Index in 'requests' where this session's dispatches start. Dispatches loaded from
    // the history file (older sessions) are before this index and are NOT shown as live trips.
    private int sessionStart = 0;

    public Main() {
        setTitle("MediRoute - Emergency Response System");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        setSize(Math.min(1350, (int) (screen.width * 0.95)),
                Math.min(780, (int) (screen.height * 0.95)));
        setMinimumSize(new Dimension(Math.min(1100, screen.width), Math.min(650, screen.height)));
        setLocationRelativeTo(null);
        // The window opens maximized (fills the whole screen) but the user can still
        // resize it, because the layouts are flexible and the minimum size is small.
        setExtendedState(JFrame.MAXIMIZED_BOTH);

        initializeAmbulances();
        initializeHospitals();

        homePage = new HomePage(this);
        emergencyPage = new EmergencyPage(this);
        trackingPage = new TrackingPage(this);
        bedsPage = new BedsPage(this);
        hospitalsPage = new HospitalsPage(this);
        historyPage = new HistoryPage(this);
        servicesPage = new ServicesPage(this);
        aboutPage = new AboutPage(this);


        pages.setBackground(BG);
        pages.add(homePage, "Home");
        pages.add(emergencyPage, "Emergency");
        pages.add(trackingPage, "Tracking");
        pages.add(bedsPage, "Beds");
        pages.add(hospitalsPage, "Hospitals");
        pages.add(historyPage, "History");
        pages.add(servicesPage, "Services");
        pages.add(aboutPage, "About");


        header = new HeaderBar(this);
        header.setVisible(false);
        footer = new FooterBar(this);
        footer.setVisible(false);

        pages.add(new LoginPage(() -> {
            header.setVisible(true);
            footer.setVisible(true);
            showPage("Home");
        }), "Login");

        setLayout(new BorderLayout());
        add(header, BorderLayout.NORTH);
        add(pages, BorderLayout.CENTER);
        add(footer, BorderLayout.SOUTH);

        showPage("Login");
    }

    private void initializeAmbulances() {
        ambulances.add(new AmbulanceUnit("MED-34", "Available", "Main Station"));
        ambulances.add(new AmbulanceUnit("MED-74", "Available", "North Station"));
        ambulances.add(new AmbulanceUnit("MED-81", "Available", "Central Station"));
        ambulances.add(new AmbulanceUnit("MED-92", "On Route", "University Road"));
    }

    private void initializeHospitals() {
        hospitals.add(new HospitalData("City Emergency Hospital", 2.4, "Emergency, Trauma, ICU", 30, 18, "021-111-100-200"));
        hospitals.add(new HospitalData("MediCare Central", 4.1, "Emergency, General, Pediatric", 50, 27, "021-111-200-300"));
        hospitals.add(new HospitalData("LifeLine Medical Center", 5.7, "ICU, CCU, Emergency", 40, 11, "021-111-300-400"));
        hospitals.add(new HospitalData("Prime Health Hospital", 7.2, "General, Surgical, Emergency", 60, 23, "021-111-400-500"));
    }

    // Suggests a hospital from the entered location. For this offline Swing demo,
    // familiar area keywords are used first; otherwise the closest hospital with
    // an available bed is returned. This keeps the feature functional without
    // requiring an external maps/GPS API.
    public HospitalData findSuggestedHospital(String location) {
        String text = location == null ? "" : location.toLowerCase();
        String[] keywords = {"north", "university", "main", "campus"};
        for (String k : keywords) {
            if (text.contains(k) && hospitals.size() > 0 && hospitals.get(0).getAvailableBeds() > 0)
                return hospitals.get(0);
        }
        String[] central = {"central", "saddar", "downtown", "city"};
        for (String k : central) {
            if (text.contains(k) && hospitals.size() > 1 && hospitals.get(1).getAvailableBeds() > 0)
                return hospitals.get(1);
        }
        String[] south = {"clifton", "south", "life", "sea"};
        for (String k : south) {
            if (text.contains(k) && hospitals.size() > 2 && hospitals.get(2).getAvailableBeds() > 0)
                return hospitals.get(2);
        }
        String[] east = {"airport", "east", "prime"};
        for (String k : east) {
            if (text.contains(k) && hospitals.size() > 3 && hospitals.get(3).getAvailableBeds() > 0)
                return hospitals.get(3);
        }
        return findNearestHospitalWithBed();
    }

    // Switches the visible page, then asks that page to refresh itself.
    // POLYMORPHISM: each refreshData() call runs the version written in that
    // specific page class.
    public void showPage(String pageName) {
        cardLayout.show(pages, pageName);
        if (pageName.equals("Home")) homePage.refreshData();
        if (pageName.equals("Tracking")) trackingPage.refreshData();
        if (pageName.equals("Beds")) bedsPage.refreshData();
        if (pageName.equals("Hospitals")) hospitalsPage.refreshData();
        if (pageName.equals("History")) historyPage.refreshData();
        if (pageName.equals("Emergency")) emergencyPage.refreshData();
        if (pageName.equals("Services")) servicesPage.refreshData();
        if (pageName.equals("About")) aboutPage.refreshData();

    }

    // ENCAPSULATION: public getter gives controlled access to the private list.
    static void fitToScreen(Window w, int width, int height) {
        Rectangle s = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        w.setSize(Math.min(width, (int) (s.width * 0.92)),
                Math.min(height, (int) (s.height * 0.92)));
    }
    static File resolve(String path) {
        File f = new File(path);
        if (f.exists()) return f;
        try {
            File jarDir = new File(Main.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI()).getParentFile();
            File g = new File(jarDir, path);
            if (g.exists()) return g;
        } catch (Exception ignored) { }
        return f;
    }
    public String nextRequestId() {
        return "MED-" + (++requestCounter);
    }
    public List<EmergencyRequest> getRequests() {
        return requests;
    }

    // Stores the request in memory, saves it to history.txt (FILE HANDLING),
    // and updates the tracking and history pages.
    public void addRequest(EmergencyRequest request) {
        requests.add(request);
        FileManager.saveRequest(request);
        trackingPage.setCurrentRequest(request);
        historyPage.refreshData();
    }

    // [NEW] BED SYNC: when a dispatch is made, the selected hospital loses 1 bed.
    // 1) the hospital's total available-bed count goes down by 1
    // 2) the free count of that hospital's ward on the Beds page goes down by 1
    //    (ward is chosen from the emergency type: Cardiac -> CCU, Trauma -> Trauma Unit ...)
    public void reserveBedFor(HospitalData hospital, String emergencyType) {
        hospital.reserveBed();
        int hospitalIndex = hospitals.indexOf(hospital);
        BedsPage.takeWardBed(hospitalIndex, emergencyType);
        bedsPage.refreshData();   // Beds page foran naye numbers dikhaye
    }

    // Keeps new IDs unique after old dispatches are loaded from the history file.
    // EXCEPTION HANDLING: a badly formatted ID is ignored instead of crashing.
    void bumpCounterFrom(String id) {
        try {
            int n = Integer.parseInt(id.replace("MED-", "").trim());
            if (n > requestCounter) requestCounter = n;
        } catch (NumberFormatException ignored) { }
    }

    public EmergencyRequest getLatestRequest() {
        if (requests.size() <= sessionStart) return null;   // only this session's dispatches
        return requests.get(requests.size() - 1);
    }

    public AmbulanceUnit findNearestFreeAmbulance() {
        for (AmbulanceUnit a : ambulances) {
            if (a.getStatus().equals("Available")) return a;
        }
        return null;
    }

    public HospitalData findNearestHospitalWithBed() {
        HospitalData selected = null;
        for (HospitalData h : hospitals) {
            if (h.getAvailableBeds() > 0) {
                if (selected == null || h.getDistance() < selected.getDistance()) selected = h;
            }
        }
        return selected;
    }

    // =========================================================
    // HEADER (WITH LOGO)
    // INHERITANCE: HeaderBar extends JPanel.
    // =========================================================
    static class HeaderBar extends JPanel {

        HeaderBar(final Main app) {
            setBackground(new Color(14, 18, 22));
            setBorder(new EmptyBorder(12, 22, 12, 22));
            setLayout(new BorderLayout());

            String logoHtml = "<html><b style='font-size:22px'>Medi"
                    + "<span style='color:#F52D2D'>Route</span></b></html>";

            JLabel logo;
            ImageIcon logoIcon = new ImageIcon(Main.resolve("images/logo.png").getPath());

            if (logoIcon.getIconWidth() > 0) {
                Image scaled = logoIcon.getImage().getScaledInstance(40, 40, Image.SCALE_SMOOTH);
                logo = new JLabel(logoHtml, new ImageIcon(scaled), SwingConstants.LEFT);
                logo.setIconTextGap(10);
            } else {
                logo = new JLabel(logoHtml);
            }

            logo.setForeground(TEXT);
            logo.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            logo.setToolTipText("Go to Home");
            logo.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) { app.showPage("Home"); }
            });
            add(logo, BorderLayout.WEST);

            JPanel nav = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 0));
            nav.setOpaque(false);

            String[] names = {"Home", "Emergency", "Tracking", "Beds", "Hospitals", "Services", "History", "About"};

            for (final String name : names) {
                JButton button = new RoundedButton(name, name.equals("Emergency"));
                button.addActionListener(e -> app.showPage(name));
                nav.add(button);
            }

            add(nav, BorderLayout.CENTER);

            JButton request = new RoundedButton("REQUEST AMBULANCE", true);
            request.addActionListener(e -> app.showPage("Emergency"));
            add(request, BorderLayout.EAST);
        }
    }


    // =========================================================
    // FOOTER (shown on every page after login): logo + social icons
    // INHERITANCE: FooterBar extends JPanel.
    // =========================================================
    static class FooterBar extends JPanel {

        // CHANGE THESE to your real social pages
        private static final String FACEBOOK_URL = "https://www.facebook.com/";
        private static final String INSTAGRAM_URL = "https://www.instagram.com/";
        private static final String TWITTER_URL = "https://twitter.com/";

        FooterBar(final Main app) {
            setBackground(new Color(14, 18, 22));
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(46, 53, 62)),
                    new EmptyBorder(14, 28, 14, 28)));
            setLayout(new BorderLayout(20, 0));

            // ---- logo (same as header) ----
            String logoHtml = "<html><b style='font-size:20px'>Medi<span style='color:#F52D2D'>Route</span></b>"
                    + "<br><span style='font-size:10px;color:#A0A5AA'>Getting patients to care faster.</span></html>";
            JLabel logo;
            ImageIcon icon = new ImageIcon(Main.resolve("images/logo.png").getPath());
            if (icon.getIconWidth() > 0) {
                Image scaled = icon.getImage().getScaledInstance(40, 40, Image.SCALE_SMOOTH);
                logo = new JLabel(logoHtml, new ImageIcon(scaled), SwingConstants.LEFT);
                logo.setIconTextGap(10);
            } else {
                logo = new JLabel(logoHtml);
            }
            logo.setForeground(TEXT);
            logo.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            logo.setToolTipText("Go to Home");
            logo.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) { app.showPage("Home"); }
            });
            add(logo, BorderLayout.WEST);

            // ---- social icons ----
            JPanel social = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 4));
            social.setOpaque(false);
            social.add(new SocialIcon("facebook", FACEBOOK_URL));
            social.add(new SocialIcon("instagram", INSTAGRAM_URL));
            social.add(new SocialIcon("twitter", TWITTER_URL));
            add(social, BorderLayout.CENTER);

            // ---- emergency + copyright ----
            JLabel info = new JLabel("<html><div style='text-align:right'><span style='color:#F52D2D'><b>Emergency: 1122</b></span>"
                    + "<br><span style='font-size:10px;color:#A0A5AA'>&copy; 2026 MediRoute. All rights reserved.</span></div></html>");
            info.setForeground(TEXT);
            info.setFont(new Font("SansSerif", Font.PLAIN, 12));
            add(info, BorderLayout.EAST);
        }

        // Round social icon drawn with Graphics2D (no image files needed).
        // Click opens the link in the default browser.
        static class SocialIcon extends JComponent {
            private final String type;
            private boolean hover = false;

            SocialIcon(final String type, final String url) {
                this.type = type;
                Dimension d = new Dimension(38, 38);
                setPreferredSize(d);
                setMinimumSize(d);
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                setToolTipText(type.substring(0, 1).toUpperCase() + type.substring(1));
                addMouseListener(new MouseAdapter() {
                    @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                    @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
                    @Override public void mouseClicked(MouseEvent e) {
                        try {
                            if (Desktop.isDesktopSupported())
                                Desktop.getDesktop().browse(new java.net.URI(url));
                        } catch (Exception ex) {
                            Dialogs.showMessageDialog(null, "Could not open: " + url);
                        }
                    }
                });
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                int w = getWidth(), h = getHeight();
                int cx = w / 2, cy = h / 2;

                // grows slightly on hover
                if (hover) g2.scale(1.08, 1.08);
                if (hover) g2.translate(-w * 0.04, -h * 0.04);

                // ---- real brand colour background ----
                switch (type) {
                    case "facebook":
                        g2.setColor(hover ? new Color(66, 133, 255) : new Color(24, 119, 242));   // Facebook blue
                        g2.fillOval(1, 1, w - 3, h - 3);
                        break;
                    case "instagram":
                        g2.setPaint(new LinearGradientPaint(new Point(0, h), new Point(w, 0),
                                new float[]{0f, 0.25f, 0.5f, 0.75f, 1f},
                                new Color[]{new Color(254, 218, 117), new Color(250, 126, 30),
                                        new Color(214, 41, 118), new Color(150, 47, 191),
                                        new Color(79, 91, 213)}));                                  // Instagram gradient
                        g2.fillOval(1, 1, w - 3, h - 3);
                        break;
                    default:   // X / Twitter
                        g2.setColor(hover ? new Color(45, 50, 56) : new Color(0, 0, 0));          // X black
                        g2.fillOval(1, 1, w - 3, h - 3);
                        g2.setColor(new Color(255, 255, 255, hover ? 230 : 140));
                        g2.setStroke(new BasicStroke(1.4f));
                        g2.drawOval(1, 1, w - 3, h - 3);
                }

                // ---- white logo on top ----
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                switch (type) {
                    case "facebook": {
                        g2.setFont(new Font("SansSerif", Font.BOLD, 24));
                        FontMetrics fm = g2.getFontMetrics();
                        g2.drawString("f", cx - fm.stringWidth("f") / 2 + 2, cy + fm.getAscent() / 2 - 3);
                        break;
                    }
                    case "instagram":
                        g2.setStroke(new BasicStroke(1.9f));
                        g2.drawRoundRect(cx - 8, cy - 8, 16, 16, 7, 7);
                        g2.drawOval(cx - 4, cy - 4, 8, 8);
                        g2.fillOval(cx + 3, cy - 7, 3, 3);
                        break;
                    default:
                        g2.drawLine(cx - 6, cy - 7, cx + 6, cy + 7);
                        g2.drawLine(cx + 6, cy - 7, cx - 6, cy + 7);
                }
                g2.dispose();
            }
        }
    }

    // =========================================================
    // ABSTRACT PAGE
    // =========================================================
    /*
     * ABSTRACTION: AppPage is an abstract class. It describes what every page
     * must be able to do (refreshData) without saying how. Each page supplies
     * its own version, and the rest of the app only needs to know it is an
     * AppPage.
     *
     * INHERITANCE: AppPage extends JPanel. HomePage, EmergencyPage,
     * TrackingPage, TeleCarePage, BedsPage, HospitalsPage, ServicesPage,
     * AboutPage and HistoryPage all extend AppPage, so they reuse the shared
     * setup (background, layout, title() and subtitle() helpers).
     *
     * ENCAPSULATION: the reference to the app is protected, so only AppPage
     * and its subclasses can use it.
     */
    static abstract class AppPage extends JPanel {

        protected final Main app;

        AppPage(Main app) {
            this.app = app;
            setBackground(BG);
            setLayout(new BorderLayout());
        }

        // ABSTRACTION: abstract method with no body. Every subclass must
        // implement it. (POLYMORPHISM: each page overrides it differently.)
        abstract void refreshData();

        // Shared helper inherited by every page (code reuse through inheritance).
        protected JLabel title(String text) {
            JLabel label = new JLabel(text);
            label.setForeground(TEXT);
            label.setFont(new Font("SansSerif", Font.BOLD, 30));
            return label;
        }

        protected JLabel subtitle(String text) {
            JLabel label = new JLabel(text);
            label.setForeground(MUTED);
            label.setFont(new Font("SansSerif", Font.PLAIN, 14));
            return label;
        }
    }

    // =========================================================
    // HOME PAGE
    // INHERITANCE: HomePage extends AppPage.
    // =========================================================
    static class HomePage extends AppPage {

        private Image bgImage;
        private final List<AnimatedCard> statCards = new ArrayList<AnimatedCard>();

        HomePage(Main app) {
            super(app);

            bgImage = loadBackground("images/home_bg.png");

            JPanel content = new JPanel(new BorderLayout(0, 18));
            content.setOpaque(false);
            content.setBorder(new EmptyBorder(22, 32, 24, 32));

            JPanel hero = new JPanel(new BorderLayout(18, 0));
            hero.setOpaque(false);
            hero.setBorder(new EmptyBorder(28, 28, 28, 28));

            JPanel left = new JPanel();
            left.setOpaque(false);
            left.setPreferredSize(new Dimension(390, 330));
            left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));

            JLabel tag = new JLabel("SMART EMERGENCY NETWORK");
            tag.setForeground(RED);
            tag.setFont(new Font("SansSerif", Font.BOLD, 12));

            JLabel heading = new JLabel("<html><div style='line-height:115%'>EMERGENCY<br>RESPONSE,<br>"
                    + "<span style='color:#F52D2D'>REDEFINED.</span></div></html>");
            heading.setForeground(TEXT);
            heading.setFont(new Font("SansSerif", Font.BOLD, 39));

            JLabel desc = new JLabel("<html>Optimizing critical routes with smart transit<br>"
                    + "lanes for life-saving precision.</html>");
            desc.setForeground(MUTED);
            desc.setFont(new Font("SansSerif", Font.PLAIN, 14));

            JButton call = new RoundedButton("EMERGENCY CALL", true);
            call.addActionListener(e -> app.showPage("Emergency"));

            left.add(tag);
            left.add(Box.createVerticalStrut(10));
            left.add(heading);
            left.add(Box.createVerticalStrut(12));
            left.add(desc);
            left.add(Box.createVerticalStrut(20));
            left.add(call);

            JPanel ambulanceCanvas = new LightsCanvas();

            JPanel stats = new JPanel();
            stats.setOpaque(false);
            stats.setPreferredSize(new Dimension(240, 330));
            stats.setLayout(new BoxLayout(stats, BoxLayout.Y_AXIS));

            JLabel statTitle = new JLabel("REAL-TIME RESPONSE STATS");
            statTitle.setForeground(TEXT);
            statTitle.setFont(new Font("SansSerif", Font.BOLD, 13));

            stats.add(statTitle);
            stats.add(Box.createVerticalStrut(12));
            stats.add(statCard("AVG. RESPONSE TIME", "7m 42s", "↘"));
            stats.add(Box.createVerticalStrut(9));
            stats.add(statCard("ACTIVE AMBULANCES", "124", "▣"));
            stats.add(Box.createVerticalStrut(9));
            stats.add(statCard("SUCCESSFUL ROUTES", "98%", "✓"));

            hero.add(left, BorderLayout.WEST);
            hero.add(ambulanceCanvas, BorderLayout.CENTER);
            hero.add(stats, BorderLayout.EAST);

            JPanel features = new JPanel(new GridLayout(1, 3, 14, 0));
            features.setOpaque(false);
            features.setPreferredSize(new Dimension(100, 120));
            features.add(new FeatureCard("SMART ROUTING", "Live traffic aware routes for every ambulance.",
                    () -> app.showPage("Tracking")));
            features.add(new FeatureCard("BED AVAILABILITY", "Real-time beds across nearby hospitals.",
                    () -> app.showPage("Beds")));


            content.add(hero, BorderLayout.CENTER);
            content.add(features, BorderLayout.SOUTH);
            add(content, BorderLayout.CENTER);
        }

        // [CHANGED] Home page stat card. The video was removed:
        // the card now shows only a label + value (no click / WATCH / video).
        private JPanel statCard(String label, String value, String icon) {
            final AnimatedCard card = new AnimatedCard();
            statCards.add(card);                       // list mein rakho taa-ke refreshData() animation chala sake
            card.setLayout(new BorderLayout(10, 0));
            card.setBorder(new EmptyBorder(10, 12, 10, 12));
            card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));

            // left side: red icon
            JLabel ic = new JLabel(icon);
            ic.setForeground(RED);
            ic.setFont(new Font("Dialog", Font.BOLD, 22));

            // middle: small label on top, big value below
            JPanel text = new JPanel();
            text.setOpaque(false);
            text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));

            JLabel l = new JLabel(label);
            l.setForeground(MUTED);
            l.setFont(new Font("SansSerif", Font.BOLD, 9));

            JLabel v = new JLabel(value);
            v.setForeground(TEXT);
            v.setFont(new Font("SansSerif", Font.BOLD, 20));

            text.add(l);
            text.add(v);

            card.add(ic, BorderLayout.WEST);
            card.add(text, BorderLayout.CENTER);
            return card;
        }

        private JPanel featureCard(String head, String body) {
            JPanel card = new HoverPanel();
            card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
            card.setBorder(new EmptyBorder(16, 18, 16, 18));

            JLabel h = new JLabel(head);
            h.setForeground(RED);
            h.setFont(new Font("SansSerif", Font.BOLD, 13));

            JLabel b = new JLabel("<html>" + body + "</html>");
            b.setForeground(MUTED);

            card.add(h);
            card.add(Box.createVerticalStrut(8));
            card.add(b);
            return card;
        }

        // FILE HANDLING + EXCEPTION HANDLING:
        // Reads the background image from disk. It checks that the file exists,
        // then catches IOException (file cannot be read), OutOfMemoryError
        // (image too large) and a general Exception. On any failure it prints a
        // message and returns null, so the app keeps running without an image.
        private Image loadBackground(String path) {
            try {
                File file = Main.resolve(path);
                if (!file.exists()) {
                    System.out.println("Background image not found: " + file.getAbsolutePath());
                    return null;
                }
                Image img = ImageIO.read(file);
                if (img == null) {
                    System.out.println("Unsupported image format: " + path);
                    return null;
                }
                return img;
            } catch (IOException ex) {
                System.out.println("Could not read background image: " + ex.getMessage());
                return null;
            } catch (OutOfMemoryError ex) {
                System.out.println("Background image is too large to load.");
                return null;
            } catch (Exception ex) {
                System.out.println("Background image error: " + ex.getMessage());
                return null;
            }
        }

        // POLYMORPHISM: overrides JPanel's paintComponent() to draw the
        // background image.
        // EXCEPTION HANDLING: try/catch/finally. If drawing fails, the image is
        // dropped; finally always releases the Graphics2D object.
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (bgImage == null) return;

            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BILINEAR);

                int w = getWidth(), h = getHeight();
                int iw = bgImage.getWidth(null), ih = bgImage.getHeight(null);
                if (iw <= 0 || ih <= 0 || w <= 0 || h <= 0) return;

                double scale = Math.max((double) w / iw, (double) h / ih);
                int dw = (int) (iw * scale), dh = (int) (ih * scale);
                g2.drawImage(bgImage, (w - dw) / 2, (h - dh) / 2, dw, dh, null);

                g2.setPaint(new GradientPaint(0, 0, new Color(10, 14, 18, 235),
                        w, 0, new Color(10, 14, 18, 70)));
                g2.fillRect(0, 0, w, h);

                g2.setPaint(new GradientPaint(0, h - 160, new Color(10, 14, 18, 0),
                        0, h, new Color(10, 14, 18, 220)));
                g2.fillRect(0, h - 160, w, 160);

            } catch (Exception ex) {
                System.out.println("Background drawing failed: " + ex.getMessage());
                bgImage = null;
            } finally {
                g2.dispose();
            }
        }

        // POLYMORPHISM (method overriding): HomePage's own version of the
        // abstract refreshData(). It replays the stat card animations.
        @Override
        void refreshData() {
            int delay = 0;
            for (AnimatedCard c : statCards) {
                c.play(delay);
                delay += 150;
            }
        }
    }

    // =========================================================
    // AMBULANCE CANVAS (HOME)
    // INHERITANCE: extends JPanel.
    // =========================================================
    // BLINKING AMBULANCE LIGHTS (Home page). Tune LIGHT_X / LIGHT_Y.
    static class LightsCanvas extends JPanel {

        private static final double LIGHT_X = 0.55;   // left -> right
        private static final double LIGHT_Y = 0.42;   // top  -> bottom

        private final Timer timer;

        LightsCanvas() {
            setOpaque(false);
            timer = new Timer(30, e -> repaint());
        }

        @Override
        public void addNotify() { super.addNotify(); timer.start(); }

        @Override
        public void removeNotify() { timer.stop(); super.removeNotify(); }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth(), h = getHeight();
            double cx = w * LIGHT_X, cy = h * LIGHT_Y;
            long ms = System.currentTimeMillis();
            boolean flash = (ms / 220) % 2 == 0;
            double ang = (ms % 1600) / 1600.0 * 360.0;

            // rotating beacon beams (red one side, blue the other)
            for (int k = 0; k < 2; k++) {
                Color c = k == 0 ? new Color(255, 40, 40) : new Color(60, 120, 255);
                double R = 230;
                g2.setPaint(new RadialGradientPaint(new java.awt.geom.Point2D.Double(cx, cy), (float) R,
                        new float[]{0f, 1f},
                        new Color[]{new Color(c.getRed(), c.getGreen(), c.getBlue(), 110),
                                new Color(c.getRed(), c.getGreen(), c.getBlue(), 0)}));
                g2.fill(new Arc2D.Double(cx - R, cy - R, 2 * R, 2 * R, ang + k * 180, 34, Arc2D.PIE));
            }

            // blinking glow (red, then blue)
            Color red = new Color(255, 40, 40, flash ? 200 : 40);
            Color blue = new Color(60, 120, 255, flash ? 40 : 200);
            g2.setPaint(new RadialGradientPaint(new java.awt.geom.Point2D.Double(cx - 26, cy), 80f,
                    new float[]{0f, 1f}, new Color[]{red, new Color(255, 40, 40, 0)}));
            g2.fillOval((int) (cx - 106), (int) (cy - 80), 160, 160);
            g2.setPaint(new RadialGradientPaint(new java.awt.geom.Point2D.Double(cx + 26, cy), 80f,
                    new float[]{0f, 1f}, new Color[]{blue, new Color(60, 120, 255, 0)}));
            g2.fillOval((int) (cx - 54), (int) (cy - 80), 160, 160);

            // the two lamps
            g2.setColor(flash ? new Color(255, 70, 70) : new Color(110, 25, 25));
            g2.fillRoundRect((int) cx - 46, (int) cy - 7, 42, 14, 8, 8);
            g2.setColor(flash ? new Color(25, 45, 100) : new Color(80, 140, 255));
            g2.fillRoundRect((int) cx + 4, (int) cy - 7, 42, 14, 8, 8);
            g2.dispose();
        }
    }

    static class AmbulanceCanvas extends JPanel {

        AmbulanceCanvas() {
            setOpaque(false);
        }

        // POLYMORPHISM: overrides paintComponent() to draw an ambulance.
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth(), h = getHeight();

            g2.setColor(new Color(30, 36, 42));
            g2.fillRect(0, h - 90, w, 60);
            g2.setColor(new Color(90, 95, 100));
            for (int x = 0; x < w; x += 60) g2.fillRect(x, h - 62, 30, 4);

            int x = w / 2 - 110;
            int y = h - 190;

            g2.setColor(Color.WHITE);
            g2.fillRoundRect(x, y, 150, 75, 12, 12);
            g2.fillRoundRect(x + 150, y + 25, 55, 50, 12, 12);
            g2.setColor(new Color(70, 80, 86));
            g2.fillRoundRect(x + 158, y + 32, 34, 22, 6, 6);

            g2.setColor(RED);
            g2.fillRect(x, y + 50, 150, 9);
            g2.setColor(Color.WHITE);
            g2.fillRect(x + 62, y + 28, 26, 9);
            g2.fillRect(x + 70, y + 20, 10, 25);

            g2.setColor(Color.BLACK);
            g2.fillOval(x + 20, y + 62, 27, 27);
            g2.fillOval(x + 150, y + 62, 27, 27);

            g2.setColor(RED);
            g2.fillRoundRect(x + 90, y - 7, 24, 9, 5, 5);
            g2.dispose();
        }
    }

    // =========================================================
    // EMERGENCY PAGE
    // INHERITANCE: EmergencyPage extends AppPage.
    // ENCAPSULATION: all form fields are private.
    // =========================================================
    static class EmergencyPage extends AppPage {

        // [CHANGED] Pehle ek hi "Patient / Caller Name" field tha.
        // Caller and patient now have separate inputs.
        private final JTextField callerField = new JTextField();    // caller ka naam
        private final JTextField patientField = new JTextField();   // patient ka naam
        private final JTextField phoneField = new JTextField();
        private final JTextField locationField = new JTextField();

        // Hospital suggestions are updated from the location entered by the user.
        private final JComboBox<HospitalData> hospitalSuggestion = new JComboBox<HospitalData>();
        private final JLabel validationHint = new JLabel(" ");

        private final JComboBox<String> emergencyType = new JComboBox<String>(
                new String[]{"Cardiac", "Trauma", "General", "Critical Patient", "Accident"});

        private final JComboBox<String> priority = new JComboBox<String>(
                new String[]{"Critical", "High", "Normal"});

        private final JTextArea notes = new JTextArea(5, 20);

        EmergencyPage(Main app) {
            super(app);

            JPanel outer = new JPanel(new BorderLayout());
            outer.setOpaque(false);
            outer.setBorder(new EmptyBorder(30, 60, 30, 60));

            JPanel top = new JPanel();
            top.setOpaque(false);
            top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
            top.add(title("REQUEST AN AMBULANCE"));
            top.add(Box.createVerticalStrut(7));
            top.add(subtitle("Enter emergency information and dispatch an ambulance."));

            // Added one row for the hospital recommendation/selection.
            // 8 rows: Caller, Patient, Phone, Location, Hospital, Type, Priority, Notes
            JPanel form = new JPanel(new GridLayout(8, 2, 15, 10));
            form.setBackground(PANEL);
            form.setBorder(new EmptyBorder(20, 25, 20, 25));

            addField(form, "Caller Name", callerField);
            addField(form, "Patient Name", patientField);
            addField(form, "Caller Phone Number", phoneField);
            addField(form, "Patient Location", locationField);
            addField(form, "Recommended Hospital", hospitalSuggestion);
            addField(form, "Emergency Type", emergencyType);
            addField(form, "Priority", priority);

            notes.setLineWrap(true);
            notes.setWrapStyleWord(true);
            notes.setBackground(new Color(31, 36, 42));
            notes.setForeground(TEXT);
            notes.setCaretColor(TEXT);
            notes.setBorder(new EmptyBorder(8, 8, 8, 8));

            form.add(label("Additional Notes"));
            form.add(new JScrollPane(notes));

            validationHint.setForeground(MUTED);
            validationHint.setFont(new Font("SansSerif", Font.BOLD, 11));
            validationHint.setBorder(new EmptyBorder(4, 8, 4, 8));

            JPanel bottom = new JPanel(new BorderLayout());
            bottom.setOpaque(false);
            bottom.add(validationHint, BorderLayout.WEST);

            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            buttons.setOpaque(false);

            JButton clear = new RoundedButton("CLEAR", false);
            clear.addActionListener(e -> clearForm());

            JButton dispatch = new RoundedButton("DISPATCH AMBULANCE", true);
            dispatch.addActionListener(e -> submitRequest());

            buttons.add(clear);
            buttons.add(dispatch);
            bottom.add(buttons, BorderLayout.EAST);

            JPanel center = new JPanel(new BorderLayout(0, 15));
            center.setOpaque(false);
            center.add(form, BorderLayout.CENTER);
            center.add(bottom, BorderLayout.SOUTH);

            outer.add(top, BorderLayout.NORTH);
            outer.add(center, BorderLayout.CENTER);
            add(outer, BorderLayout.CENTER);

            styleField(callerField);
            styleField(patientField);
            styleField(phoneField);
            styleField(locationField);
            styleField(hospitalSuggestion);

            // Location: digits cannot be typed or pasted (they are silently dropped).
            ((javax.swing.text.AbstractDocument) locationField.getDocument()).setDocumentFilter(
                    new javax.swing.text.DocumentFilter() {
                        private String clean(String t) { return t == null ? null : t.replaceAll("[0-9]", ""); }
                        @Override
                        public void insertString(FilterBypass fb, int off, String t, javax.swing.text.AttributeSet a)
                                throws javax.swing.text.BadLocationException {
                            String c = clean(t);
                            if (c != null && c.length() != t.length()) Toolkit.getDefaultToolkit().beep();
                            super.insertString(fb, off, c, a);
                        }
                        @Override
                        public void replace(FilterBypass fb, int off, int len, String t, javax.swing.text.AttributeSet a)
                                throws javax.swing.text.BadLocationException {
                            String c = clean(t);
                            if (c != null && c.length() != t.length()) Toolkit.getDefaultToolkit().beep();
                            super.replace(fb, off, len, c, a);
                        }
                    });

            // Caller and Patient names: only letters and spaces are allowed.
            // Digits / symbols that are typed or pasted are silently dropped (with a beep).
            lettersOnly(callerField);
            lettersOnly(patientField);

            // Strong live validation: name cannot contain digits, phone accepts
            // exactly an 11-digit Pakistani mobile number starting with 03.
            javax.swing.event.DocumentListener listener = new javax.swing.event.DocumentListener() {
                public void insertUpdate(javax.swing.event.DocumentEvent e) { liveValidate(); }
                public void removeUpdate(javax.swing.event.DocumentEvent e) { liveValidate(); }
                public void changedUpdate(javax.swing.event.DocumentEvent e) { liveValidate(); }
            };
            callerField.getDocument().addDocumentListener(listener);
            patientField.getDocument().addDocumentListener(listener);
            phoneField.getDocument().addDocumentListener(listener);
            locationField.getDocument().addDocumentListener(listener);

            // Every location change refreshes the hospital recommendation.
            locationField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                public void insertUpdate(javax.swing.event.DocumentEvent e) { refreshHospitalSuggestions(); }
                public void removeUpdate(javax.swing.event.DocumentEvent e) { refreshHospitalSuggestions(); }
                public void changedUpdate(javax.swing.event.DocumentEvent e) { refreshHospitalSuggestions(); }
            });

            refreshHospitalSuggestions();
            liveValidate();
        }

        // [NEW] Only letters and spaces can be typed in the field (used by the name fields).
        private void lettersOnly(JTextField field) {
            ((javax.swing.text.AbstractDocument) field.getDocument()).setDocumentFilter(
                    new javax.swing.text.DocumentFilter() {
                        private String clean(String t) { return t == null ? null : t.replaceAll("[^A-Za-z ]", ""); }
                        @Override
                        public void insertString(FilterBypass fb, int off, String t, javax.swing.text.AttributeSet a)
                                throws javax.swing.text.BadLocationException {
                            String c = clean(t);
                            if (c != null && c.length() != t.length()) Toolkit.getDefaultToolkit().beep();
                            super.insertString(fb, off, c, a);
                        }
                        @Override
                        public void replace(FilterBypass fb, int off, int len, String t, javax.swing.text.AttributeSet a)
                                throws javax.swing.text.BadLocationException {
                            String c = clean(t);
                            if (c != null && c.length() != t.length()) Toolkit.getDefaultToolkit().beep();
                            super.replace(fb, off, len, c, a);
                        }
                    });
        }

        private void addField(JPanel panel, String fieldLabel, JComponent field) {
            panel.add(label(fieldLabel));
            styleField(field);
            panel.add(field);
        }

        private JLabel label(String text) {
            JLabel label = new JLabel(text);
            label.setForeground(TEXT);
            label.setFont(new Font("SansSerif", Font.BOLD, 14));
            return label;
        }

        private void styleField(JComponent field) {
            field.setBackground(new Color(31, 36, 42));
            field.setForeground(TEXT);
            field.setFont(new Font("SansSerif", Font.PLAIN, 14));

            if (field instanceof JTextField) {
                JTextField tf = (JTextField) field;
                tf.setCaretColor(TEXT);
                tf.setBorder(new EmptyBorder(8, 8, 8, 8));
            }
        }

        // Updates the recommendation list whenever the patient location changes.
        // This is a demo/desktop location matcher, so it uses location keywords
        // plus the hospital's stored distance rather than claiming GPS access.
        private void refreshHospitalSuggestions() {
            String location = locationField.getText().trim().toLowerCase();
            HospitalData preferred = app.findSuggestedHospital(location);

            HospitalData[] ordered = new HospitalData[app.hospitals.size()];
            int index = 0;
            if (preferred != null) ordered[index++] = preferred;
            for (HospitalData h : app.hospitals) {
                if (h != preferred) ordered[index++] = h;
            }

            Object old = hospitalSuggestion.getSelectedItem();
            hospitalSuggestion.removeAllItems();
            for (HospitalData h : ordered) {
                if (h != null && h.getAvailableBeds() > 0) hospitalSuggestion.addItem(h);
            }

            if (old != null) {
                for (int i = 0; i < hospitalSuggestion.getItemCount(); i++) {
                    if (hospitalSuggestion.getItemAt(i).getName().equals(old.toString())) {
                        hospitalSuggestion.setSelectedIndex(i);
                        return;
                    }
                }
            }
            if (hospitalSuggestion.getItemCount() > 0) hospitalSuggestion.setSelectedIndex(0);
        }

        // A valid location: starts with a letter, only letters / spaces / , . ' - /,
        // 3-100 characters, at least one word of 3+ letters, no digits, and no
        // junk like "aaaa" (same letter 4+ times in a row).
        private static boolean isValidLocation(String loc) {
            if (!loc.matches("[A-Za-z][A-Za-z .,'/\\-]{2,99}")) return false;
            if (!loc.matches(".*[A-Za-z]{3,}.*")) return false;
            if (loc.toLowerCase().matches(".*(.)\\1{3,}.*")) return false;
            return true;
        }

        // Updates the hint label while typing (red = invalid, green = valid).
        private void liveValidate() {
            String caller = callerField.getText().trim();
            String patient = patientField.getText().trim();
            String phone = phoneField.getText().trim();

            if (!caller.isEmpty() && !caller.matches("[A-Za-z ]{2,50}")) {
                validationHint.setText("Invalid caller name: letters and spaces only.");
                validationHint.setForeground(RED);
                return;
            }
            if (!patient.isEmpty() && !patient.matches("[A-Za-z ]{2,50}")) {
                validationHint.setText("Invalid patient name: letters and spaces only.");
                validationHint.setForeground(RED);
                return;
            }
            if (!phone.isEmpty() && !phone.matches("03[0-9]{9}")) {
                validationHint.setText("Invalid phone: use exactly 11 digits starting with 03.");
                validationHint.setForeground(RED);
                return;
            }
            String loc = locationField.getText().trim();
            if (!loc.isEmpty() && !isValidLocation(loc)) {
                validationHint.setText("Invalid location: enter a real place name (letters only, no digits), e.g. Gulshan-e-Iqbal, Karachi.");
                validationHint.setForeground(RED);
                return;
            }
            if (!caller.isEmpty() && !patient.isEmpty() && !phone.isEmpty() && !loc.isEmpty()) {
                validationHint.setText("✓ Caller, patient, phone and location look valid.");
                validationHint.setForeground(GREEN);
            } else {
                validationHint.setText("Names: letters only  •  Phone: 03XXXXXXXXX (11 digits)");
                validationHint.setForeground(MUTED);
            }
        }

        // EXCEPTION HANDLING: validates every field before creating a request.
        // The phone rule is intentionally strict: exactly 11 digits and starts 03.
        private void validate(String caller, String patient, String phone, String location, String noteText) {
            if (caller.isEmpty() || patient.isEmpty() || phone.isEmpty() || location.isEmpty()) {
                throw new IllegalArgumentException("Please fill Caller Name, Patient Name, Phone and Location.");
            }
            if (!caller.matches("[A-Za-z ]{2,50}")) {
                throw new IllegalArgumentException("Caller name must contain letters and spaces only (2-50 characters). Digits are not allowed.");
            }
            if (!patient.matches("[A-Za-z ]{2,50}")) {
                throw new IllegalArgumentException("Patient name must contain letters and spaces only (2-50 characters). Digits are not allowed.");
            }
            if (!phone.matches("03[0-9]{9}")) {
                throw new IllegalArgumentException("Invalid phone number. Enter exactly 11 digits starting with 03, e.g. 03001234567.");
            }
            if (location.length() < 3 || location.length() > 100) {
                throw new IllegalArgumentException("Location must be 3-100 characters.");
            }
            if (!isValidLocation(location)) {
                throw new IllegalArgumentException("Invalid patient location. Enter a valid place name using letters only (digits are not allowed), e.g. Gulshan-e-Iqbal, Karachi.");
            }
            if (noteText.length() > 500) {
                throw new IllegalArgumentException("Notes are limited to 500 characters.");
            }
            if (location.contains("<") || location.contains(">")
                    || noteText.contains("<") || noteText.contains(">")) {
                throw new IllegalArgumentException("Location and notes cannot contain < or >.");
            }
        }

        private void submitRequest() {
            try {
                // Step 1: read the data from the form
                String caller = callerField.getText().trim();
                String patient = patientField.getText().trim();
                String phone = phoneField.getText().trim();
                String location = locationField.getText().trim();
                String noteText = notes.getText().trim();

                validate(caller, patient, phone, location, noteText);   // galat input par exception

                String id = app.nextRequestId();
                String selectedType = emergencyType.getSelectedItem().toString();
                String selectedPriority = priority.getSelectedItem().toString();

                EmergencyRequest request = new EmergencyRequest(
                        id, caller, patient, phone, location, selectedType,
                        selectedPriority, noteText, new Date());

                AmbulanceUnit ambulance = app.findNearestFreeAmbulance();
                HospitalData hospital = (HospitalData) hospitalSuggestion.getSelectedItem();
                if (hospital == null) hospital = app.findSuggestedHospital(location);

                if (ambulance != null) {
                    ambulance.setStatus("On Route");
                    ambulance.setCurrentLocation(location);
                    request.setAmbulanceId(ambulance.getId());
                } else {
                    request.setAmbulanceId("WAITING");
                }

                if (hospital != null && hospital.getAvailableBeds() > 0) {
                    request.setHospitalName(hospital.getName());
                    request.setHospitalDistance(hospital.getDistance());
                    // [NEW] Step 4: the selected hospital loses 1 bed. Both the Beds and Hospitals
                    // pages show this same live count.
                    app.reserveBedFor(hospital, selectedType);
                } else {
                    request.setHospitalName("No bed available");
                }

                // Step 2 + 3: the request is saved -> logged in History + set on the Tracking page
                app.addRequest(request);

                String confirmation = "Ambulance dispatched successfully!\n\n"
                        + "Dispatch ID: " + id + "\n"
                        + "Caller: " + caller + "\n"
                        + "Patient: " + patient + "\n"
                        + "Ambulance: " + request.getAmbulanceId() + "\n"
                        + "Selected Hospital: " + request.getHospitalName() + "\n"
                        + "Estimated Distance: " + String.format("%.1f km", request.getHospitalDistance())
                        + "\n\nTracking will follow this selected hospital.";

                if (selectedPriority.equals("Critical")) {
                    playAlert();
                    Dialogs.showMessageDialog(this,
                            "CRITICAL EMERGENCY ALERT!\n\n" + confirmation,
                            "MediRoute ALERT", Dialogs.WARNING_MESSAGE);
                } else {
                    Dialogs.showMessageDialog(this, confirmation,
                            "MediRoute", Dialogs.INFORMATION_MESSAGE);
                }

                clearForm();
                app.showPage("Tracking");

            } catch (IllegalArgumentException ex) {
                Dialogs.showMessageDialog(this, ex.getMessage(), "Input Error", Dialogs.WARNING_MESSAGE);
            } catch (Exception ex) {
                Dialogs.showMessageDialog(this, "Unexpected error: " + ex.getMessage(),
                        "Error", Dialogs.ERROR_MESSAGE);
            }
        }

        private void playAlert() {
            final int[] count = {0};
            Timer timer = new Timer(180, e -> {
                Toolkit.getDefaultToolkit().beep();
                count[0]++;
                if (count[0] >= 3) ((Timer) e.getSource()).stop();
            });
            timer.setRepeats(true);
            timer.start();
        }

        private void clearForm() {
            callerField.setText("");
            patientField.setText("");
            phoneField.setText("");
            locationField.setText("");
            emergencyType.setSelectedIndex(0);
            priority.setSelectedIndex(0);
            notes.setText("");
            refreshHospitalSuggestions();
        }

        @Override
        void refreshData() {
            refreshHospitalSuggestions();
            liveValidate();
        }
    }

    // =========================================================
    // TRACKING PAGE
    // INHERITANCE: TrackingPage extends AppPage.
    // =========================================================
    static class TrackingPage extends AppPage {

        private static final Color ORANGE = new Color(255, 125, 50);
        private static final Color CYAN = new Color(45, 205, 235);

        private final MapCanvas mapCanvas;
        private final PopupBox dispatchPanel;
        private final PopupBox crewPanel;
        private final PopupBox trafficPanel;

        // [NEW] Body of the crew panel. Rebuilt on every dispatch for the assigned ambulance.
        private final JPanel crewBody = new JPanel();
        private String crewShownFor = "";

        // [NEW] Crew of each ambulance: {Team Lead, Driver, Paramedic}
        private static final Map<String, String[]> CREW = new HashMap<String, String[]>();
        static {
            CREW.put("MED-34", new String[]{"Dr. Arva Sharma", "Ahmed Khan", "Sarah Jones"});
            CREW.put("MED-74", new String[]{"Dr. Imran Qureshi", "Bilal Hussain", "Nida Fatima"});
            CREW.put("MED-81", new String[]{"Dr. Sana Malik", "Usman Ali", "Hira Tariq"});
            CREW.put("MED-92", new String[]{"Dr. Faisal Raza", "Kamran Shah", "Ayesha Noor"});
        }

        private final JLabel idValue = new JLabel("No active dispatch");
        private final JLabel locationValue = new JLabel("Waiting for request...");
        private final JLabel etaValue = new JLabel("--");
        private final JLabel distanceValue = new JLabel("--");
        private final JLabel ambulanceValue = new JLabel("--");
        private final JLabel hospitalValue = new JLabel("--");
        private final JLabel statusBadge = new JLabel("NO ACTIVE DISPATCH");
        private final JLabel statusLine = new JLabel("Waiting for request...");
        private final JLabel unitLabel = new JLabel("Unit: --");

        private final Map<String, Trip> trips = new LinkedHashMap<String, Trip>();
        private Trip current;
        private EmergencyRequest popupDoneFor;
        private final Timer simTimer;

        TrackingPage(Main app) {
            super(app);

            JPanel outer = new JPanel(new BorderLayout(0, 14));
            outer.setOpaque(false);
            outer.setBorder(new EmptyBorder(18, 30, 22, 30));

            mapCanvas = new MapCanvas();

            dispatchPanel = dispatchBox();
            crewPanel = crewBox();
            trafficPanel = trafficBox();

            JPanel bottom = new JPanel(new GridLayout(1, 3, 14, 0));
            bottom.setOpaque(false);
            bottom.setPreferredSize(new Dimension(10, 178));
            bottom.add(dispatchPanel);
            bottom.add(crewPanel);
            bottom.add(trafficPanel);

            outer.add(mapCanvas, BorderLayout.CENTER);
            outer.add(bottom, BorderLayout.SOUTH);
            add(outer, BorderLayout.CENTER);

            simTimer = new Timer(40, e -> tick());
            simTimer.start();
        }

        private PopupBox baseInfoBox(String heading, JComponent rightSide) {
            PopupBox panel = new PopupBox();
            panel.setBorder(new EmptyBorder(14, 16, 12, 16));
            panel.setLayout(new BorderLayout(0, 9));

            JLabel h = new JLabel(heading);
            h.setForeground(TEXT);
            h.setFont(new Font("SansSerif", Font.BOLD, 12));

            JPanel head = new JPanel(new BorderLayout());
            head.setOpaque(false);
            head.add(h, BorderLayout.WEST);
            if (rightSide != null) head.add(rightSide, BorderLayout.EAST);

            panel.add(head, BorderLayout.NORTH);
            return panel;
        }

        private PopupBox dispatchBox() {
            statusBadge.setForeground(MUTED);
            statusBadge.setFont(new Font("SansSerif", Font.BOLD, 11));

            PopupBox panel = baseInfoBox("TRACK YOUR DISPATCH", statusBadge);

            JPanel body = new JPanel(new GridLayout(1, 2, 16, 0));
            body.setOpaque(false);

            JPanel left = new JPanel();
            left.setOpaque(false);
            left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));

            idValue.setForeground(RED);
            idValue.setFont(new Font("SansSerif", Font.BOLD, 12));
            left.add(infoRow("Dispatch ID", idValue));
            left.add(Box.createVerticalStrut(7));

            locationValue.setForeground(TEXT);
            locationValue.setFont(new Font("SansSerif", Font.BOLD, 11));
            left.add(infoRow("Location", locationValue));
            left.add(Box.createVerticalStrut(7));

            ambulanceValue.setForeground(TEXT);
            ambulanceValue.setFont(new Font("SansSerif", Font.BOLD, 11));
            left.add(infoRow("Ambulance", ambulanceValue));
            left.add(Box.createVerticalStrut(7));

            hospitalValue.setForeground(TEXT);
            hospitalValue.setFont(new Font("SansSerif", Font.BOLD, 11));
            left.add(infoRow("Hospital", hospitalValue));

            JPanel right = new JPanel();
            right.setOpaque(false);
            right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));

            statusLine.setForeground(MUTED);
            statusLine.setFont(new Font("SansSerif", Font.PLAIN, 11));
            statusLine.setAlignmentX(Component.LEFT_ALIGNMENT);
            right.add(statusLine);
            right.add(Box.createVerticalStrut(14));

            JPanel metrics = new JPanel(new GridLayout(1, 2, 10, 0));
            metrics.setOpaque(false);
            metrics.setAlignmentX(Component.LEFT_ALIGNMENT);
            metrics.add(smallMetric("DISTANCE", distanceValue));
            metrics.add(smallMetric("ETA", etaValue));
            right.add(metrics);

            body.add(left);
            body.add(right);
            panel.add(body, BorderLayout.CENTER);
            return panel;
        }

        private JPanel infoRow(String name, JLabel value) {
            JPanel row = new JPanel(new BorderLayout(8, 0));
            row.setOpaque(false);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
            JLabel n = new JLabel(name);
            n.setForeground(MUTED);
            n.setFont(new Font("SansSerif", Font.PLAIN, 11));
            row.add(n, BorderLayout.WEST);
            row.add(value, BorderLayout.EAST);
            return row;
        }

        private PopupBox crewBox() {
            unitLabel.setForeground(RED);
            unitLabel.setFont(new Font("SansSerif", Font.BOLD, 14));

            PopupBox panel = baseInfoBox("DISPATCHED CREW", unitLabel);
            crewBody.setOpaque(false);
            crewBody.setLayout(new BoxLayout(crewBody, BoxLayout.Y_AXIS));
            showCrewFor("WAITING");          // shuru mein "not assigned"

            panel.add(crewBody, BorderLayout.CENTER);
            return panel;
        }

        // [NEW] Fills the crew panel from the assigned ambulance's crew.
        // If no ambulance was found (WAITING) it shows "Not assigned yet".
        private void showCrewFor(String ambulanceId) {
            if (ambulanceId.equals(crewShownFor)) return;      // pehle se wahi crew dikh rahi hai
            crewShownFor = ambulanceId;
            String[] crew = CREW.get(ambulanceId);
            if (crew == null) crew = new String[]{"Not assigned yet", "Not assigned yet", "Not assigned yet"};

            crewBody.removeAll();
            crewBody.add(Box.createVerticalStrut(4));
            crewBody.add(crewLine("Team Lead", crew[0], new Color(190, 120, 90)));
            crewBody.add(Box.createVerticalStrut(6));
            crewBody.add(crewLine("Driver", crew[1], new Color(110, 140, 190)));
            crewBody.add(Box.createVerticalStrut(6));
            crewBody.add(crewLine("Paramedic", crew[2], new Color(190, 110, 150)));
            crewBody.revalidate();
            crewBody.repaint();
        }

        private PopupBox trafficBox() {
            PopupBox panel = baseInfoBox("LIVE TRAFFIC UPDATES", null);
            JPanel body = new JPanel();
            body.setOpaque(false);
            body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

            body.add(trafficLine("Main Street", "Congested • Re-routing", RED));
            body.add(Box.createVerticalStrut(8));
            body.add(trafficLine("University Road", "Congested • Smart route active", RED));
            body.add(Box.createVerticalStrut(8));
            body.add(trafficLine("Hospital Road", "Clearing • Route active", GREEN));

            panel.add(body, BorderLayout.CENTER);
            return panel;
        }

        private JPanel smallMetric(String metricTitle, JLabel value) {
            JPanel panel = new JPanel();
            panel.setOpaque(false);
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

            JLabel t = new JLabel(metricTitle);
            t.setForeground(MUTED);
            t.setFont(new Font("SansSerif", Font.BOLD, 9));

            value.setForeground(TEXT);
            value.setFont(new Font("SansSerif", Font.BOLD, 16));

            panel.add(t);
            panel.add(value);
            return panel;
        }

        private JPanel crewLine(String role, String name, Color avatarColor) {
            JPanel row = new JPanel(new GridLayout(1, 2, 6, 0));
            row.setOpaque(false);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

            JLabel a = new JLabel(role);
            a.setForeground(MUTED);
            a.setFont(new Font("SansSerif", Font.PLAIN, 12));

            JPanel who = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            who.setOpaque(false);

            String[] parts = name.replace("Dr. ", "").split(" ");
            String initials = parts.length > 1
                    ? ("" + parts[0].charAt(0) + parts[1].charAt(0))
                    : ("" + parts[0].charAt(0));

            JLabel b = new JLabel(name);
            b.setForeground(TEXT);
            b.setFont(new Font("SansSerif", Font.BOLD, 12));

            who.add(new Avatar(initials, avatarColor));
            who.add(b);

            row.add(a);
            row.add(who);
            return row;
        }

        private JPanel trafficLine(String trafficTitle, String value, Color dotColor) {
            JPanel panel = new JPanel(new BorderLayout(8, 0));
            panel.setOpaque(false);
            panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));

            JLabel dot = new JLabel("●");
            dot.setForeground(dotColor);
            dot.setFont(new Font("SansSerif", Font.PLAIN, 10));
            dot.setVerticalAlignment(SwingConstants.TOP);

            JPanel text = new JPanel();
            text.setOpaque(false);
            text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));

            JLabel a = new JLabel(trafficTitle);
            a.setForeground(TEXT);
            a.setFont(new Font("SansSerif", Font.BOLD, 11));

            JLabel b = new JLabel(value);
            b.setForeground(MUTED);
            b.setFont(new Font("SansSerif", Font.PLAIN, 10));

            text.add(a);
            text.add(b);
            panel.add(dot, BorderLayout.WEST);
            panel.add(text, BorderLayout.CENTER);
            return panel;
        }

        private String shorten(String s, int max) {
            if (s == null) return "";
            return s.length() > max ? s.substring(0, max - 1) + "..." : s;
        }

        // ---------- NEW / CHANGED PART ----------

        void setCurrentRequest(EmergencyRequest request) {
            Trip trip = trips.get(request.getId());
            if (trip == null) {
                trip = new Trip(request);
                trips.put(request.getId(), trip);
            }
            current = trip;
            mapCanvas.setTrip(trip);
            updateLabels();
        }

        private void tick() {
            for (Trip t : trips.values()) {
                if (t.isDone()) continue;

                String st = app.historyPage.statusOf(t.request);
                if (st.equals("Canceled")) { t.cancel(); continue; }
                if (st.equals("Complete")) { t.finish(); continue; }

                if (t.isWaiting()) {
                    AmbulanceUnit a = app.findNearestFreeAmbulance();
                    if (a != null) {
                        a.setStatus("On Route");
                        a.setCurrentLocation(t.request.getLocation());
                        t.request.setAmbulanceId(a.getId());
                        t.assign(a.getId());
                    }
                    continue;
                }

                int event = t.advance();
                if (event == 1 && t == current) {
                    crewPanel.showPopup("Patient On Board\nHeading to "
                            + shorten(t.request.getHospitalName(), 16), ORANGE);
                }
                if (event == 2) {
                    app.historyPage.closeRequest(t.request, "Complete");
                    if (t == current) {
                        dispatchPanel.showPopup("Arrived at Hospital\n"
                                + shorten(t.request.getHospitalName(), 18), GREEN);
                    }
                }
            }
            // SIREN: plays while any ambulance is driving, stops when all have
            // reached the hospital (or were canceled / completed).
            boolean ambulanceMoving = false;
            for (Trip t : trips.values()) {
                if (!t.isDone() && !t.isWaiting()) { ambulanceMoving = true; break; }
            }
            if (ambulanceMoving) SirenPlayer.start(); else SirenPlayer.stop();

            updateLabels();
            mapCanvas.repaint();
        }

        private void updateLabels() {
            Trip t = current;
            if (t == null) return;
            EmergencyRequest r = t.request;

            idValue.setText(r.getId());
            locationValue.setText(shorten(r.getLocation(), 20));
            locationValue.setToolTipText(r.getLocation());
            ambulanceValue.setText(r.getAmbulanceId());
            hospitalValue.setText(shorten(r.getHospitalName(), 20));
            hospitalValue.setToolTipText(r.getHospitalName());
            distanceValue.setText(String.format("%.1f km", t.remainingKm()));
            etaValue.setText(t.etaText());
            statusLine.setText(t.statusText());
            unitLabel.setText("Unit: " + r.getAmbulanceId());
            showCrewFor(r.getAmbulanceId());      // crew details dispatch ke sath update

            if (t.isCanceled()) {
                statusBadge.setText("DISPATCH CANCELED");
                statusBadge.setForeground(RED);
            } else if (t.isArrived()) {
                statusBadge.setText("ARRIVED");
                statusBadge.setForeground(GREEN);
            } else if (t.isWaiting()) {
                statusBadge.setText("WAITING");
                statusBadge.setForeground(ORANGE);
            } else {
                statusBadge.setText("DISPATCH ACTIVE");
                statusBadge.setForeground(GREEN);
            }
        }

        private void firePopups(final EmergencyRequest r) {
            dispatchPanel.showPopup("Dispatch Confirmed\n" + r.getId() + " • " + shorten(r.getName(), 18), GREEN);

            Timer t1 = new Timer(600, e -> crewPanel.showPopup(
                    "Crew Alerted\nUnit " + r.getAmbulanceId() + " is on the way", ORANGE));
            t1.setRepeats(false);
            t1.start();

            Timer t2 = new Timer(1200, e -> trafficPanel.showPopup(
                    "Smart Route Active\nClearing path to " + shorten(r.getHospitalName(), 18), CYAN));
            t2.setRepeats(false);
            t2.start();
        }

        @Override
        void refreshData() {
            EmergencyRequest request = current != null ? current.request : app.getLatestRequest();
            if (request != null) {
                setCurrentRequest(request);
                if (popupDoneFor != request) {
                    popupDoneFor = request;
                    firePopups(request);
                }
            }
        }
    }

    // =========================================================
    // POPUP BOX (rounded dark panel with toast message)
    // INHERITANCE: extends JPanel.
    // =========================================================
    static class PopupBox extends JPanel {

        private boolean hovered = false;
        private String toast;
        private Color accent = GREEN;
        private float alpha = 0f;
        private int phase = 0;
        private int hold = 0;
        private Timer anim;

        PopupBox() {
            setOpaque(false);
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hovered = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hovered = false;
                    repaint();
                }
            });
        }

        void showPopup(String message, Color accentColor) {
            toast = message;
            accent = accentColor;
            alpha = 0f;
            phase = 1;
            hold = 0;
            if (anim == null) {
                anim = new Timer(30, e -> tick());
            }
            anim.start();
        }

        private void tick() {
            if (phase == 1) {
                alpha += 0.1f;
                if (alpha >= 1f) {
                    alpha = 1f;
                    phase = 2;
                }
            } else if (phase == 2) {
                hold++;
                if (hold > 110) phase = 3;
            } else if (phase == 3) {
                alpha -= 0.06f;
                if (alpha <= 0f) {
                    alpha = 0f;
                    phase = 0;
                    toast = null;
                    anim.stop();
                }
            }
            repaint();
        }

        // POLYMORPHISM: overrides paintComponent() to draw the rounded box.
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(hovered ? new Color(28, 33, 41) : new Color(21, 26, 33));
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
            g2.setColor(hovered ? new Color(245, 45, 45, 150) : new Color(46, 53, 62));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
            g2.dispose();
            super.paintComponent(g);
        }

        // POLYMORPHISM: overrides paint() to draw the toast on top of the
        // children after the normal painting is done.
        @Override
        public void paint(Graphics g) {
            super.paint(g);
            if (toast == null || alpha <= 0f) return;

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            String[] lines = toast.split("\n");
            int bw = getWidth() - 24;
            int bh = 22 + lines.length * 18;
            int bx = 12;
            int by = (getHeight() - bh) / 2 + (int) ((1f - alpha) * 14);

            int a = (int) (alpha * 255);
            g2.setColor(new Color(8, 11, 15, (int) (alpha * 238)));
            g2.fillRoundRect(bx, by, bw, bh, 14, 14);
            g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), a));
            g2.setStroke(new BasicStroke(1.6f));
            g2.drawRoundRect(bx, by, bw, bh, 14, 14);

            // icon circle with check
            int ic = 22;
            int ix = bx + 12;
            int iy = by + (bh - ic) / 2;
            g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), a));
            g2.fillOval(ix, iy, ic, ic);
            g2.setColor(new Color(10, 14, 18, a));
            g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawLine(ix + 6, iy + 11, ix + 10, iy + 15);
            g2.drawLine(ix + 10, iy + 15, ix + 16, iy + 7);

            int tx = ix + ic + 10;
            int ty = by + 20;
            for (int i = 0; i < lines.length; i++) {
                if (i == 0) {
                    g2.setFont(new Font("SansSerif", Font.BOLD, 12));
                    g2.setColor(new Color(255, 255, 255, a));
                } else {
                    g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
                    g2.setColor(new Color(185, 192, 200, a));
                }
                g2.drawString(lines[i], tx, ty + i * 17);
            }
            g2.dispose();
        }
    }

    // =========================================================
    // CREW AVATAR
    // INHERITANCE: extends JComponent.
    // =========================================================
    static class Avatar extends JComponent {

        private final String initials;
        private final Color color;

        Avatar(String initials, Color color) {
            this.initials = initials;
            this.color = color;
            setPreferredSize(new Dimension(26, 26));
        }

        // POLYMORPHISM: overrides paintComponent() to draw a round avatar.
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setColor(color);
            g2.fillOval(1, 1, 24, 24);
            g2.setColor(new Color(235, 235, 235));
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawOval(1, 1, 24, 24);
            g2.setFont(new Font("SansSerif", Font.BOLD, 10));
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(Color.WHITE);
            g2.drawString(initials, (26 - fm.stringWidth(initials)) / 2, (26 + fm.getAscent()) / 2 - 2);
            g2.dispose();
        }
    }
    // =========================================================
    // MAP
    // INHERITANCE: MapCanvas extends JPanel.
    // =========================================================
    static class MapCanvas extends JPanel {

        private static final String[] H_NAMES = {"Main Street", "University Road", "Hospital Road"};
        private static final String[] V_NAMES = {"Garden Avenue", "Central Avenue", "Ring Road", "Harbor Road"};
        private static final String[] HOSP_LABELS = {"City Emergency", "MediCare", "LifeLine", "Prime Health"};

        private Trip trip;
        private double zoom = 1.0, panX = 0, panY = 0;
        private Point dragFrom;
        private int worldW = 1, worldH = 1;

        MapCanvas() {
            setPreferredSize(new Dimension(700, 400));
            setOpaque(false);

            MouseAdapter ma = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) { dragFrom = e.getPoint(); }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if (dragFrom == null) return;
                    panX += e.getX() - dragFrom.x;
                    panY += e.getY() - dragFrom.y;
                    dragFrom = e.getPoint();
                    clampPan();
                    repaint();
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2) {
                        zoom = 1.0;
                        panX = 0;
                        panY = 0;
                        repaint();
                    }
                }

                @Override
                public void mouseWheelMoved(MouseWheelEvent e) {
                    zoom *= e.getWheelRotation() < 0 ? 1.15 : 1 / 1.15;
                    zoom = Math.max(1.0, Math.min(3.5, zoom));
                    clampPan();
                    repaint();
                }
            };
            addMouseListener(ma);
            addMouseMotionListener(ma);
            addMouseWheelListener(ma);
        }

        void setTrip(Trip t) {
            if (t != trip) {
                zoom = 1.0;
                panX = 0;
                panY = 0;
            }
            trip = t;
            repaint();
        }

        private void clampPan() {
            double mx = getWidth() * (zoom - 1) / 2.0;
            double my = getHeight() * (zoom - 1) / 2.0;
            panX = Math.max(-mx, Math.min(mx, panX));
            panY = Math.max(-my, Math.min(my, panY));
        }

        // ---------------------------------------------------------
        // PAINT
        // ---------------------------------------------------------
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

            int w = getWidth(), h = getHeight();
            worldW = w;
            worldH = h;
            Shape oldClip = g2.getClip();
            g2.setClip(new RoundRectangle2D.Float(0, 0, w, h, 18, 18));

            AffineTransform base = g2.getTransform();
            g2.translate(w / 2.0 + panX, h / 2.0 + panY);
            g2.scale(zoom, zoom);
            g2.translate(-w / 2.0, -h / 2.0);
            drawWorld(g2, w, h);
            g2.setTransform(base);

            drawHud(g2, w, h);

            g2.setClip(oldClip);
            g2.setColor(new Color(46, 53, 62));
            g2.setStroke(new BasicStroke(1f));
            g2.drawRoundRect(0, 0, w - 1, h - 1, 18, 18);
            g2.dispose();
        }

        private void drawWorld(Graphics2D g2, int w, int h) {
            g2.setColor(new Color(15, 19, 24));
            g2.fillRect(0, 0, w, h);

            // city blocks and parks
            Random pr = new Random(5);
            for (int c = 0; c < Trip.COLS - 1; c++) {
                for (int r = 0; r < Trip.ROWS - 1; r++) {
                    boolean park = pr.nextInt(100) < 14;
                    int x1 = (int) (Trip.nx(c) * w) + 7, y1 = (int) (Trip.ny(r) * h) + 7;
                    int x2 = (int) (Trip.nx(c + 1) * w) - 7, y2 = (int) (Trip.ny(r + 1) * h) - 7;
                    g2.setColor(park ? new Color(20, 44, 38) : new Color(19, 24, 30));
                    g2.fillRoundRect(x1, y1, x2 - x1, y2 - y1, 8, 8);
                }
            }

            // roads (every 3rd row / 4th column is a wide main road)
            for (int r = 0; r < Trip.ROWS; r++) {
                boolean art = r % 3 == 1;
                int y = (int) (Trip.ny(r) * h);
                g2.setStroke(new BasicStroke(art ? 7f : 3.5f));
                g2.setColor(art ? new Color(40, 48, 59) : new Color(31, 38, 47));
                g2.drawLine(0, y, w, y);
                if (art) {
                    g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f,
                            new float[]{8f, 8f}, 0f));
                    g2.setColor(new Color(80, 90, 104, 110));
                    g2.drawLine(0, y, w, y);
                }
            }
            for (int c = 0; c < Trip.COLS; c++) {
                boolean art = c % 4 == 1;
                int x = (int) (Trip.nx(c) * w);
                g2.setStroke(new BasicStroke(art ? 7f : 3.5f));
                g2.setColor(art ? new Color(40, 48, 59) : new Color(31, 38, 47));
                g2.drawLine(x, 0, x, h);
                if (art) {
                    g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f,
                            new float[]{8f, 8f}, 0f));
                    g2.setColor(new Color(80, 90, 104, 110));
                    g2.drawLine(x, 0, x, h);
                }
            }

            // street names
            g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
            g2.setColor(new Color(105, 114, 126));
            for (int r = 1; r < Trip.ROWS; r += 3) {
                int y = (int) (Trip.ny(r) * h);
                g2.drawString(H_NAMES[r / 3], (int) (w * 0.12), y - 7);
                g2.drawString(H_NAMES[r / 3], (int) (w * 0.62), y - 7);
            }
            for (int c = 1; c < Trip.COLS; c += 4) {
                int x = (int) (Trip.nx(c) * w);
                drawRotatedText(g2, V_NAMES[c / 4], x - 6, (int) (h * 0.62), -90);
            }

            Trip t = trip;

            // heavy traffic on this trip's roads
            if (t != null) {
                for (double[] e : t.congested) {
                    drawTraffic(g2, (int) (e[0] * w), (int) (e[1] * h), (int) (e[2] * w), (int) (e[3] * h));
                }
            }

            // hospitals (small markers; the destination gets a big pin later)
            for (int i = 0; i < Trip.HOSPITAL_NODES.length; i++) {
                int[] hn = Trip.HOSPITAL_NODES[i];
                if (t != null && hn[0] == t.hospitalNode[0] && hn[1] == t.hospitalNode[1]) continue;
                drawSmallHospital(g2, (int) (Trip.nx(hn[0]) * w), (int) (Trip.ny(hn[1]) * h), HOSP_LABELS[i]);
            }

            if (t == null) return;

            // routes
            double[] amb = null;
            if (t.toPatient.size() >= 2 && !t.isWaiting()) {
                double p1 = t.phase == 0 ? t.progress : 1;
                double[] pos = drawLeg(g2, t.toPatient, w, h, p1, false);
                if (t.phase == 0) amb = pos;
            }
            double p2 = t.phase == 2 ? t.progress : (t.phase == 3 ? 1 : 0);
            double[] pos2 = drawLeg(g2, t.toHospital, w, h, p2, true);
            if (!t.isWaiting() && t.phase >= 1) amb = pos2;

            int px = (int) (Trip.nx(t.patientNode[0]) * w), py = (int) (Trip.ny(t.patientNode[1]) * h);
            int hx = (int) (Trip.nx(t.hospitalNode[0]) * w), hy = (int) (Trip.ny(t.hospitalNode[1]) * h);

            if (t.stationNode != null) {
                drawStation(g2, (int) (Trip.nx(t.stationNode[0]) * w), (int) (Trip.ny(t.stationNode[1]) * h));
            }

            // hospital halo + pin, patient pin
            g2.setColor(new Color(245, 45, 45, 45));
            g2.fillOval(hx - 24, hy - 24, 48, 48);
            drawPin(g2, hx, hy, new Color(255, 110, 100), new Color(200, 40, 45), "H");
            drawPin(g2, px, py, new Color(255, 190, 90), new Color(220, 120, 20), "P");

            if (amb != null) drawAmbulance(g2, amb, t);

            // labels
            drawTag(g2, "Patient: " + cut(t.request.getName(), 16), px + 24, py - 50, new Color(255, 160, 60));
            drawTag(g2, cut(t.request.getHospitalName(), 24), hx + 24, hy - 50, new Color(60, 66, 74));
            if (t.isArrived()) {
                drawTag(g2, "Arrived at Hospital", hx - 60, hy + 12, GREEN);
            } else if (t.isWaiting()) {
                drawTag(g2, "Waiting for a free ambulance", px + 24, py - 20, new Color(255, 170, 0));
            } else if (amb != null) {
                drawTag(g2, t.request.getAmbulanceId(), (int) amb[0] + 16, (int) amb[1] + 10, new Color(60, 66, 74));
            }
        }

        // ---------------------------------------------------------
        // ROUTE DRAWING
        // ---------------------------------------------------------
        private double[] drawLeg(Graphics2D g2, List<double[]> corners, int w, int h, double prog, boolean main) {
            if (corners.size() < 2) return null;
            List<double[]> pts = samplePath(roundedPath(corners, w, h));
            if (pts.size() < 2) return null;

            double[] cum = new double[pts.size()];
            for (int i = 1; i < pts.size(); i++) {
                cum[i] = cum[i - 1] + Math.hypot(pts.get(i)[0] - pts.get(i - 1)[0],
                        pts.get(i)[1] - pts.get(i - 1)[1]);
            }
            double total = Math.max(1, cum[cum.length - 1]);
            double target = Math.max(0, Math.min(1, prog)) * total;

            // glow
            g2.setStroke(new BasicStroke(15f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
            for (int i = 1; i < pts.size(); i++) {
                Color c = main ? routeColor(cum[i] / total) : new Color(110, 150, 215);
                g2.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 38));
                g2.draw(new Line2D.Double(pts.get(i - 1)[0], pts.get(i - 1)[1], pts.get(i)[0], pts.get(i)[1]));
            }
            // core
            g2.setStroke(new BasicStroke(5.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int i = 1; i < pts.size(); i++) {
                g2.setColor(main ? routeColor(cum[i] / total) : new Color(110, 150, 215));
                g2.draw(new Line2D.Double(pts.get(i - 1)[0], pts.get(i - 1)[1], pts.get(i)[0], pts.get(i)[1]));
            }
            // already-travelled part is greyed out
            if (target > 0) {
                g2.setColor(new Color(70, 78, 90));
                for (int i = 1; i < pts.size(); i++) {
                    if (cum[i - 1] >= target) break;
                    double[] a = pts.get(i - 1), b = pts.get(i);
                    double bx = b[0], by = b[1];
                    if (cum[i] > target && cum[i] > cum[i - 1]) {
                        double f = (target - cum[i - 1]) / (cum[i] - cum[i - 1]);
                        bx = a[0] + (b[0] - a[0]) * f;
                        by = a[1] + (b[1] - a[1]) * f;
                    }
                    g2.draw(new Line2D.Double(a[0], a[1], bx, by));
                }
            }
            return pointAt(pts, cum, target);
        }

        private double[] pointAt(List<double[]> pts, double[] cum, double target) {
            int idx = 1;
            while (idx < pts.size() - 1 && cum[idx] < target) idx++;
            double[] a = pts.get(idx - 1), b = pts.get(idx);
            double seg = cum[idx] - cum[idx - 1];
            double f = seg == 0 ? 0 : Math.max(0, Math.min(1, (target - cum[idx - 1]) / seg));
            return new double[]{a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f,
                    Math.atan2(b[1] - a[1], b[0] - a[0])};
        }

        private double[] toward(double[] from, double[] to, double d) {
            double dx = to[0] - from[0], dy = to[1] - from[1];
            double len = Math.sqrt(dx * dx + dy * dy);
            if (len == 0) return new double[]{from[0], from[1]};
            return new double[]{from[0] + dx / len * d, from[1] + dy / len * d};
        }

        private Path2D.Double roundedPath(List<double[]> corners, int w, int h) {
            int n = corners.size();
            double[][] pt = new double[n][2];
            for (int i = 0; i < n; i++) {
                pt[i][0] = corners.get(i)[0] * w;
                pt[i][1] = corners.get(i)[1] * h;
            }
            Path2D.Double p = new Path2D.Double();
            p.moveTo(pt[0][0], pt[0][1]);
            for (int i = 1; i < n - 1; i++) {
                double dPrev = Math.hypot(pt[i][0] - pt[i - 1][0], pt[i][1] - pt[i - 1][1]);
                double dNext = Math.hypot(pt[i + 1][0] - pt[i][0], pt[i + 1][1] - pt[i][1]);
                double rad = Math.min(24, Math.min(dPrev, dNext) * 0.45);
                double[] a = toward(pt[i], pt[i - 1], rad);
                double[] b = toward(pt[i], pt[i + 1], rad);
                p.lineTo(a[0], a[1]);
                p.quadTo(pt[i][0], pt[i][1], b[0], b[1]);
            }
            p.lineTo(pt[n - 1][0], pt[n - 1][1]);
            return p;
        }

        private List<double[]> samplePath(Path2D.Double path) {
            List<double[]> out = new ArrayList<double[]>();
            PathIterator it = path.getPathIterator(null, 0.5);
            double[] c = new double[6];
            while (!it.isDone()) {
                int t = it.currentSegment(c);
                if (t == PathIterator.SEG_MOVETO || t == PathIterator.SEG_LINETO) {
                    out.add(new double[]{c[0], c[1]});
                }
                it.next();
            }
            return out;
        }

        private Color routeColor(double t) {
            Color c1 = new Color(40, 110, 255);
            Color c2 = new Color(40, 215, 225);
            Color c3 = new Color(150, 235, 90);
            if (t < 0.5) return mix(c1, c2, t / 0.5);
            return mix(c2, c3, (t - 0.5) / 0.5);
        }

        private Color mix(Color a, Color b, double t) {
            t = Math.max(0, Math.min(1, t));
            return new Color(
                    (int) (a.getRed() + (b.getRed() - a.getRed()) * t),
                    (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                    (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t));
        }

        // ---------------------------------------------------------
        // MARKERS
        // ---------------------------------------------------------
        private void drawAmbulance(Graphics2D g2, double[] pos, Trip t) {
            int mx = (int) pos[0], my = (int) pos[1];
            boolean moving = !t.isDone() && t.phase != 1;
            double pulse = System.currentTimeMillis() / 220.0;
            int pr = 13 + (int) (moving ? 4 * Math.sin(pulse) : 0);

            g2.setColor(new Color(255, 255, 255, 45));
            g2.fillOval(mx - pr, my - pr, pr * 2, pr * 2);
            g2.setColor(Color.WHITE);
            g2.fillOval(mx - 9, my - 9, 18, 18);
            g2.setColor(RED);
            g2.fillRect(mx - 5, my - 1, 10, 3);
            g2.fillRect(mx - 1, my - 5, 3, 10);

            if (moving) {
                boolean flip = (System.currentTimeMillis() / 200) % 2 == 0;
                Color blue = new Color(60, 130, 255);
                g2.setColor(flip ? RED : blue);
                g2.fillOval(mx - 8, my - 16, 6, 6);
                g2.setColor(flip ? blue : RED);
                g2.fillOval(mx + 2, my - 16, 6, 6);
            }
        }

        private void drawStation(Graphics2D g2, int x, int y) {
            g2.setColor(new Color(50, 120, 255, 70));
            g2.fillRoundRect(x - 13, y - 13, 26, 26, 10, 10);
            g2.setColor(new Color(50, 120, 255));
            g2.fillRoundRect(x - 9, y - 9, 18, 18, 6, 6);
            g2.setColor(Color.WHITE);
            g2.setFont(new Font("SansSerif", Font.BOLD, 11));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString("S", x - fm.stringWidth("S") / 2, y + 4);
            g2.setFont(new Font("SansSerif", Font.PLAIN, 9));
            g2.setColor(MUTED);
            g2.drawString("Station", x - 14, y + 24);
        }

        private void drawSmallHospital(Graphics2D g2, int x, int y, String label) {
            g2.setColor(new Color(130, 40, 45, 80));
            g2.fillOval(x - 13, y - 13, 26, 26);
            g2.setColor(new Color(140, 45, 50));
            g2.fillOval(x - 9, y - 9, 18, 18);
            g2.setColor(Color.WHITE);
            g2.setFont(new Font("SansSerif", Font.BOLD, 11));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString("H", x - fm.stringWidth("H") / 2, y + 4);
            g2.setFont(new Font("SansSerif", Font.PLAIN, 9));
            g2.setColor(MUTED);
            fm = g2.getFontMetrics();
            g2.drawString(label, x - fm.stringWidth(label) / 2, y + 24);
        }

        private void drawPin(Graphics2D g2, int x, int y, Color top, Color bottom, String glyph) {
            int r = 17;
            int cy = y - r - 12;

            g2.setColor(new Color(0, 0, 0, 90));
            g2.fillOval(x - 9, y - 3, 18, 6);

            Area pin = new Area(new Ellipse2D.Double(x - r, cy - r, r * 2, r * 2));
            Path2D.Double tri = new Path2D.Double();
            tri.moveTo(x - r * 0.78, cy + r * 0.62);
            tri.lineTo(x + r * 0.78, cy + r * 0.62);
            tri.lineTo(x, y);
            tri.closePath();
            pin.add(new Area(tri));

            g2.setPaint(new GradientPaint(0, cy - r, top, 0, y, bottom));
            g2.fill(pin);
            g2.setColor(bottom.darker());
            g2.setStroke(new BasicStroke(1.4f));
            g2.draw(pin);

            g2.setColor(new Color(14, 18, 22));
            g2.fillOval(x - r + 5, cy - r + 5, (r - 5) * 2, (r - 5) * 2);
            g2.setColor(top);
            g2.setFont(new Font("SansSerif", Font.BOLD, 15));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(glyph, x - fm.stringWidth(glyph) / 2, cy + 5);
        }

        private void drawTag(Graphics2D g2, String text, int x, int y, Color border) {
            g2.setFont(new Font("SansSerif", Font.BOLD, 11));
            FontMetrics fm = g2.getFontMetrics();
            int tw = fm.stringWidth(text) + 20;
            int th = 24;
            x = Math.max(6, Math.min(x, worldW - tw - 6));
            y = Math.max(6, Math.min(y, worldH - th - 6));
            g2.setColor(new Color(10, 14, 18, 225));
            g2.fillRoundRect(x, y, tw, th, 8, 8);
            g2.setColor(border);
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawRoundRect(x, y, tw, th, 8, 8);
            g2.setColor(TEXT);
            g2.drawString(text, x + 10, y + 16);
        }

        private void drawRotatedText(Graphics2D g2, String text, int x, int y, double deg) {
            AffineTransform old = g2.getTransform();
            g2.rotate(Math.toRadians(deg), x, y);
            g2.drawString(text, x, y);
            g2.setTransform(old);
        }

        private void drawTraffic(Graphics2D g2, int x1, int y1, int x2, int y2) {
            g2.setStroke(new BasicStroke(4.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1f,
                    new float[]{5f, 4f}, 0f));
            g2.setColor(new Color(215, 55, 55, 210));
            g2.drawLine(x1, y1, x2, y2);
            g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1f,
                    new float[]{5f, 4f}, 4f));
            g2.setColor(new Color(235, 235, 235, 200));
            g2.drawLine(x1, y1, x2, y2);
        }

        private static String cut(String s, int max) {
            if (s == null) return "";
            return s.length() > max ? s.substring(0, max - 3) + "..." : s;
        }

        // ---------------------------------------------------------
        // HUD (fixed on screen, not affected by zoom / pan)
        // ---------------------------------------------------------
        private Color hudColor(Trip t) {
            if (t.isCanceled()) return RED;
            if (t.isArrived()) return GREEN;
            if (t.isWaiting()) return new Color(255, 170, 0);
            if (t.phase == 1) return new Color(255, 140, 60);
            return new Color(45, 205, 235);
        }

        private void drawHud(Graphics2D g2, int w, int h) {
            Trip t = trip;

            if (t == null) {
                String msg = "No active dispatch";
                String sub = "Request an ambulance to start live tracking.";
                g2.setColor(new Color(10, 14, 18, 215));
                g2.fillRoundRect(w / 2 - 190, h / 2 - 34, 380, 64, 14, 14);
                g2.setFont(new Font("SansSerif", Font.BOLD, 14));
                FontMetrics fm = g2.getFontMetrics();
                g2.setColor(TEXT);
                g2.drawString(msg, (w - fm.stringWidth(msg)) / 2, h / 2 - 8);
                g2.setFont(new Font("SansSerif", Font.PLAIN, 12));
                fm = g2.getFontMetrics();
                g2.setColor(MUTED);
                g2.drawString(sub, (w - fm.stringWidth(sub)) / 2, h / 2 + 14);
            } else {
                Color c = hudColor(t);
                int px = 14, py = 14, pw = 280, ph = 58;
                g2.setColor(new Color(10, 14, 18, 230));
                g2.fillRoundRect(px, py, pw, ph, 12, 12);
                g2.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 170));
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawRoundRect(px, py, pw, ph, 12, 12);

                g2.setColor(c);
                g2.fillOval(px + 12, py + 12, 9, 9);
                g2.setColor(TEXT);
                g2.setFont(new Font("SansSerif", Font.BOLD, 12));
                g2.drawString(t.statusText().toUpperCase(), px + 28, py + 21);

                g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
                g2.setColor(MUTED);
                g2.drawString("Unit " + t.request.getAmbulanceId() + " to "
                        + cut(t.request.getHospitalName(), 24), px + 12, py + 38);

                g2.setColor(new Color(45, 52, 60));
                g2.fillRoundRect(px + 12, py + ph - 12, pw - 24, 5, 5, 5);
                g2.setColor(c);
                g2.fillRoundRect(px + 12, py + ph - 12, (int) ((pw - 24) * t.overallProgress()), 5, 5, 5);
            }

            // legend
            int lx = 16, ly = h - 18;
            g2.setColor(new Color(10, 14, 18, 215));
            g2.fillRoundRect(lx - 8, ly - 15, 292, 26, 10, 10);
            g2.setFont(new Font("SansSerif", Font.PLAIN, 10));

            g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(routeColor(0.5));
            g2.drawLine(lx, ly - 3, lx + 18, ly - 3);
            g2.setColor(MUTED);
            g2.drawString("Hospital route", lx + 24, ly);

            g2.setColor(new Color(110, 150, 215));
            g2.drawLine(lx + 100, ly - 3, lx + 118, ly - 3);
            g2.setColor(MUTED);
            g2.drawString("To patient", lx + 124, ly);

            g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1f,
                    new float[]{4f, 3f}, 0f));
            g2.setColor(new Color(215, 55, 55));
            g2.drawLine(lx + 190, ly - 3, lx + 208, ly - 3);
            g2.setColor(MUTED);
            g2.drawString("Heavy traffic", lx + 214, ly);

            // hint
            String hint = "Scroll: zoom  \u2022  Drag: pan  \u2022  Double-click: reset  (" + (int) Math.round(zoom * 100) + "%)";
            g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
            FontMetrics fm = g2.getFontMetrics();
            int hw = fm.stringWidth(hint) + 20;
            g2.setColor(new Color(10, 14, 18, 215));
            g2.fillRoundRect(w - hw - 14, h - 33, hw, 24, 10, 10);
            g2.setColor(MUTED);
            g2.drawString(hint, w - hw - 4, h - 17);
        }
    }
    // =========================================================
    // TRIP: one ambulance journey  station -> patient -> hospital
    // ENCAPSULATION: route data and progress change only through
    // advance(), assign(), cancel() and finish().
    // =========================================================
    // =========================================================
    // AMBULANCE SIREN (generated in code, no sound file needed)
    // Uses javax.sound.sampled to synthesize a rising/falling "wail" and
    // loops it on a background thread until stop() is called.
    // =========================================================
    static class SirenPlayer {

        private static volatile boolean playing = false;
        private static int generation = 0;

        static synchronized void start() {
            if (playing) return;
            playing = true;
            final int gen = ++generation;
            Thread th = new Thread(() -> run(gen), "ambulance-siren");
            th.setDaemon(true);
            th.start();
        }

        static synchronized void stop() {
            playing = false;
        }

        private static void run(int gen) {
            javax.sound.sampled.SourceDataLine line = null;
            try {
                final float rate = 44100f;
                javax.sound.sampled.AudioFormat fmt =
                        new javax.sound.sampled.AudioFormat(rate, 16, 1, true, false);
                line = javax.sound.sampled.AudioSystem.getSourceDataLine(fmt);
                line.open(fmt, 8820);          // small buffer so it stops quickly
                line.start();

                final int frames = 882;        // 20 ms per chunk
                byte[] buf = new byte[frames * 2];
                double phase = 0, t = 0;

                while (playing && gen == generation) {
                    for (int i = 0; i < frames; i++) {
                        // frequency sweeps 650 Hz <-> 1400 Hz every 3 seconds
                        double sweep = 0.5 + 0.5 * Math.sin(2 * Math.PI * t / 3.0 - Math.PI / 2);
                        double f = 650 + 750 * sweep;
                        phase += 2 * Math.PI * f / rate;
                        if (phase > 2 * Math.PI) phase -= 2 * Math.PI;
                        double v = 0.7 * Math.sin(phase) + 0.2 * Math.sin(2 * phase) + 0.1 * Math.sin(3 * phase);
                        short sample = (short) (v * 0.45 * 32767);
                        buf[2 * i] = (byte) (sample & 0xFF);
                        buf[2 * i + 1] = (byte) ((sample >> 8) & 0xFF);
                        t += 1.0 / rate;
                    }
                    line.write(buf, 0, buf.length);
                }
            } catch (Exception ignored) {
                // no audio device available: the app keeps working silently
            } finally {
                if (line != null) {
                    line.stop();
                    line.flush();
                    line.close();
                }
            }
        }
    }

    static class Trip {

        static final int COLS = 14, ROWS = 8;
        private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

        static final int[][] HOSPITAL_NODES = {{10, 2}, {3, 2}, {11, 6}, {2, 6}};
        private static final String[] HOSPITAL_KEYS = {"city emergency", "medicare", "lifeline", "prime health"};

        final EmergencyRequest request;
        final int[] patientNode;
        final int[] hospitalNode;
        int[] stationNode;

        final List<double[]> toPatient = new ArrayList<double[]>();
        final List<double[]> toHospital = new ArrayList<double[]>();
        final List<double[]> congested = new ArrayList<double[]>();

        // phase 0 = driving to patient, 1 = loading patient, 2 = driving to hospital, 3 = arrived
        int phase = 0;
        double progress = 0;

        private int hold = 0;
        private boolean waiting = true;
        private boolean canceled = false;

        private final long seed;
        private final double speedFactor;
        private final double[][] hCost = new double[COLS - 1][ROWS];
        private final double[][] vCost = new double[COLS][ROWS - 1];
        private double km1 = 0, km2;
        private double sec1 = 1, sec2;
        private int edges2;

        Trip(EmergencyRequest r) {
            this.request = r;

            String loc = r.getLocation() == null ? "" : r.getLocation().trim().toLowerCase();
            seed = loc.hashCode() * 2654435761L + 17;

            String pr = r.getPriority();
            speedFactor = "Critical".equals(pr) ? 0.7 : "High".equals(pr) ? 0.85 : 1.0;
            hospitalNode = hospitalNodeFor(r.getHospitalName());

            // traffic: different for every location, so the best route differs too
            Random rc = new Random(seed + 99);
            for (int c = 0; c < COLS - 1; c++) {
                for (int rr = 0; rr < ROWS; rr++) {
                    hCost[c][rr] = roadCost(rc);
                    if (hCost[c][rr] > 2.2) congested.add(new double[]{nx(c), ny(rr), nx(c + 1), ny(rr)});
                }
            }
            for (int c = 0; c < COLS; c++) {
                for (int rr = 0; rr < ROWS - 1; rr++) {
                    vCost[c][rr] = roadCost(rc);
                    if (vCost[c][rr] > 2.2) congested.add(new double[]{nx(c), ny(rr), nx(c), ny(rr + 1)});
                }
            }

            // patient position comes from the location text
            Random rp = new Random(seed);
            int[] p = null;
            for (int i = 0; i < 300 && p == null; i++) {
                int c = rp.nextInt(COLS), rr = rp.nextInt(ROWS);
                int dist = Math.abs(c - hospitalNode[0]) + Math.abs(rr - hospitalNode[1]);
                if (dist >= 6 && !isHospitalNode(c, rr)) p = new int[]{c, rr};
            }
            if (p == null) p = new int[]{0, 0};
            patientNode = p;

            List<int[]> n2 = findPath(patientNode, hospitalNode);
            toHospital.addAll(corners(n2));
            edges2 = Math.max(1, n2.size() - 1);
            km2 = r.getHospitalDistance() > 0 ? r.getHospitalDistance() : 3.0;
            sec2 = Math.max(6, edges2 * 1.8 * speedFactor);

            if (!"WAITING".equals(r.getAmbulanceId())) assign(r.getAmbulanceId());
        }

        // ---------- helpers ----------
        static double nx(int c) { return 0.06 + c * 0.88 / (COLS - 1); }
        static double ny(int r) { return 0.10 + r * 0.80 / (ROWS - 1); }

        static boolean isHospitalNode(int c, int r) {
            for (int[] h : HOSPITAL_NODES) if (h[0] == c && h[1] == r) return true;
            return false;
        }

        static int[] hospitalNodeFor(String name) {
            String n = name == null ? "" : name.toLowerCase();
            for (int i = 0; i < HOSPITAL_KEYS.length; i++) {
                if (n.contains(HOSPITAL_KEYS[i])) return HOSPITAL_NODES[i];
            }
            return HOSPITAL_NODES[0];
        }

        private static double roadCost(Random rc) {
            return rc.nextDouble() < 0.12 ? 2.4 + rc.nextDouble() * 2.0 : 1.0 + rc.nextDouble() * 0.35;
        }

        // Gives the trip an ambulance: picks a station and plans station -> patient.
        void assign(String ambulanceId) {
            long h = ambulanceId == null ? 0 : ambulanceId.hashCode();
            Random rs = new Random(seed ^ (h * 31L));
            int[] s = null;
            for (int i = 0; i < 300 && s == null; i++) {
                int c = rs.nextInt(COLS), rr = rs.nextInt(ROWS);
                int dist = Math.abs(c - patientNode[0]) + Math.abs(rr - patientNode[1]);
                if (dist >= 4 && !isHospitalNode(c, rr)) s = new int[]{c, rr};
            }
            if (s == null) s = new int[]{COLS - 1, ROWS - 1};
            stationNode = s;

            toPatient.clear();
            List<int[]> n1 = findPath(s, patientNode);
            toPatient.addAll(corners(n1));
            int e1 = Math.max(1, n1.size() - 1);
            km1 = e1 * (km2 / edges2);
            sec1 = Math.max(4, e1 * 1.8 * speedFactor);

            waiting = false;
            phase = 0;
            progress = 0;
        }

        // Cheapest route on the street grid. Heavy traffic costs more and every
        // turn adds a small penalty, so routes look like real driving routes.
        private List<int[]> findPath(int[] a, int[] b) {
            int n = COLS * ROWS;
            double[] dist = new double[n * 4];
            int[] prev = new int[n * 4];
            Arrays.fill(dist, Double.MAX_VALUE);
            Arrays.fill(prev, -1);

            PriorityQueue<double[]> pq = new PriorityQueue<double[]>((x, y) -> Double.compare(x[0], y[0]));
            for (int d = 0; d < 4; d++) {
                int s0 = (a[1] * COLS + a[0]) * 4 + d;
                dist[s0] = 0;
                pq.add(new double[]{0, s0});
            }

            int best = -1;
            while (!pq.isEmpty()) {
                double[] cur = pq.poll();
                int s = (int) cur[1];
                if (cur[0] > dist[s]) continue;
                int d = s % 4, node = s / 4, c = node % COLS, r = node / COLS;
                if (c == b[0] && r == b[1]) { best = s; break; }

                for (int nd = 0; nd < 4; nd++) {
                    if (nd == (d ^ 1)) continue;                    // no U-turns
                    int nc = c + DIRS[nd][0], nr = r + DIRS[nd][1];
                    if (nc < 0 || nr < 0 || nc >= COLS || nr >= ROWS) continue;
                    double cost = (nr == r ? hCost[Math.min(c, nc)][r] : vCost[c][Math.min(r, nr)])
                            + (nd != d ? 0.9 : 0);
                    int ns = (nr * COLS + nc) * 4 + nd;
                    if (dist[s] + cost < dist[ns]) {
                        dist[ns] = dist[s] + cost;
                        prev[ns] = s;
                        pq.add(new double[]{dist[ns], ns});
                    }
                }
            }

            List<int[]> out = new ArrayList<int[]>();
            if (best < 0) {
                out.add(a);
                out.add(b);
                return out;
            }
            for (int s = best; s != -1; s = prev[s]) {
                int node = s / 4;
                out.add(new int[]{node % COLS, node / COLS});
            }
            java.util.Collections.reverse(out);
            return out;
        }

        // keeps only the corner points of a grid path
        private List<double[]> corners(List<int[]> nodes) {
            List<double[]> out = new ArrayList<double[]>();
            for (int i = 0; i < nodes.size(); i++) {
                if (i > 0 && i < nodes.size() - 1) {
                    int[] p = nodes.get(i - 1), q = nodes.get(i), nxt = nodes.get(i + 1);
                    if (q[0] - p[0] == nxt[0] - q[0] && q[1] - p[1] == nxt[1] - q[1]) continue;
                }
                out.add(new double[]{nx(nodes.get(i)[0]), ny(nodes.get(i)[1])});
            }
            return out;
        }

        // ---------- simulation ----------
        // Called 25 times a second. Returns 1 when the ambulance reaches the
        // patient, 2 when it reaches the hospital (and stops there), else 0.
        int advance() {
            if (waiting || canceled || phase == 3) return 0;

            if (phase == 0) {
                progress += 1.0 / (sec1 * 25);
                if (progress >= 1) {
                    progress = 1;
                    phase = 1;
                    hold = 0;
                    return 1;
                }
            } else if (phase == 1) {
                if (++hold >= 50) {
                    phase = 2;
                    progress = 0;
                }
            } else if (phase == 2) {
                progress += 1.0 / (sec2 * 25);
                if (progress >= 1) {
                    progress = 1;
                    phase = 3;
                    return 2;
                }
            }
            return 0;
        }

        void cancel() { canceled = true; }

        void finish() {
            phase = 3;
            progress = 1;
        }

        boolean isWaiting() { return waiting; }
        boolean isCanceled() { return canceled; }
        boolean isArrived() { return phase == 3; }
        boolean isDone() { return phase == 3 || canceled; }

        double remainingKm() {
            if (waiting) return km2;
            switch (phase) {
                case 0: return km1 * (1 - progress) + km2;
                case 1: return km2;
                case 2: return km2 * (1 - progress);
                default: return 0;
            }
        }

        String etaText() {
            if (canceled || waiting) return "--";
            if (phase == 3) return "Arrived";
            int m = (int) Math.ceil(remainingKm() / 35.0 * 60.0 * speedFactor);
            return Math.max(1, m) + " min";
        }

        double overallProgress() {
            if (waiting) return 0;
            double w1 = sec1 / (sec1 + sec2);
            switch (phase) {
                case 0: return w1 * progress;
                case 1: return w1;
                case 2: return w1 + (1 - w1) * progress;
                default: return 1;
            }
        }

        String statusText() {
            if (canceled) return "Dispatch canceled";
            if (waiting) return "Waiting for ambulance";
            switch (phase) {
                case 0: return "En route to patient";
                case 1: return "Patient on board";
                case 2: return "Transporting to hospital";
                default: return "Arrived at hospital";
            }
        }
    }
    // =========================================================
    // BEDS PAGE
    // INHERITANCE: BedsPage extends AppPage.
    // =========================================================
    static class BedsPage extends AppPage {

        private final JLabel available = new JLabel();
        private final JPanel cards = new JPanel(new GridLayout(2, 3, 15, 15));
        private final List<BedCard> bedCards = new ArrayList<BedCard>();

        // Ward order for the cards
        private static final String[] WARDS = {"Emergency Ward", "ICU", "CCU", "General Ward", "Pediatric", "Trauma Unit"};

        // [NEW] Emergency type -> best matching ward
        private static String wardForType(String type) {
            switch (type) {
                case "Cardiac":          return "CCU";
                case "Trauma":           return "Trauma Unit";
                case "Critical Patient": return "ICU";
                case "General":          return "General Ward";
                default:                 return "Emergency Ward";   // Accident etc.
            }
        }

        // [NEW] Remove 1 free bed from one hospital's ward.
        // If the preferred ward is full, the first ward that still has a free bed is used.
        static void takeWardBed(int hospitalIndex, String emergencyType) {
            if (hospitalIndex < 0) return;
            String preferred = wardForType(emergencyType);
            List<String> order = new ArrayList<String>();
            order.add(preferred);
            for (String w : WARDS) if (!w.equals(preferred)) order.add(w);
            for (String w : order) {
                int[][] split = WARD_SPLIT.get(w);
                if (split != null && hospitalIndex < split.length && split[hospitalIndex][1] > 0) {
                    split[hospitalIndex][1]--;
                    return;
                }
            }
        }

        // [NEW] {free, total} of one ward, summed across all hospitals.
        private static int[] wardTotals(String ward) {
            int free = 0, total = 0;
            for (int[] row : WARD_SPLIT.get(ward)) { total += row[0]; free += row[1]; }
            return new int[]{free, total};
        }

        // For every unit: {total beds, free beds} of each hospital, in the same
        // order as the hospital list (City Emergency, MediCare Central,
        // LifeLine, Prime Health). The numbers add up to the unit cards.
        // [CHANGED] These numbers are now LIVE: the free count goes down on dispatch
        // (takeWardBed) and the Beds page rebuilds its cards from them.
        private static final Map<String, int[][]> WARD_SPLIT = new HashMap<String, int[][]>();

        static {
            WARD_SPLIT.put("Emergency Ward", new int[][]{{10, 6}, {8, 5}, {6, 3}, {6, 4}});     // 30 / 18
            WARD_SPLIT.put("ICU",            new int[][]{{4, 2}, {2, 1}, {4, 2}, {2, 1}});      // 12 / 6
            WARD_SPLIT.put("CCU",            new int[][]{{2, 1}, {2, 1}, {4, 1}, {2, 1}});      // 10 / 4
            WARD_SPLIT.put("General Ward",   new int[][]{{10, 6}, {16, 10}, {10, 8}, {14, 8}}); // 50 / 32
            WARD_SPLIT.put("Pediatric",      new int[][]{{2, 1}, {8, 4}, {4, 2}, {4, 2}});      // 18 / 9
            WARD_SPLIT.put("Trauma Unit",    new int[][]{{6, 3}, {2, 1}, {2, 1}, {4, 2}});      // 14 / 7
        }

        BedsPage(Main app) {
            super(app);

            JPanel outer = new JPanel(new BorderLayout(15, 15));
            outer.setOpaque(false);
            outer.setBorder(new EmptyBorder(30, 45, 30, 45));

            JPanel heading = new JPanel(new BorderLayout());
            heading.setOpaque(false);
            heading.add(title("BED AVAILABILITY"), BorderLayout.WEST);

            available.setForeground(GREEN);
            available.setFont(new Font("SansSerif", Font.BOLD, 18));
            heading.add(available, BorderLayout.EAST);

            cards.setOpaque(false);

            outer.add(heading, BorderLayout.NORTH);
            outer.add(cards, BorderLayout.CENTER);
            add(outer, BorderLayout.CENTER);

            refreshData();
        }

        // POLYMORPHISM: BedsPage's own refreshData(). It rebuilds the ward
        // cards and replays their pop-in animation.
        @Override
        void refreshData() {
            cards.removeAll();
            bedCards.clear();

            // [CHANGED] Numbers are no longer hard-coded; they come from WARD_SPLIT (live).
            int totalAvailable = 0;
            for (String ward : WARDS) {
                int[] t = wardTotals(ward);
                addBedCard(ward, t[0], t[1]);
                totalAvailable += t[0];
            }
            available.setText("LIVE STATUS • " + totalAvailable + " TOTAL AVAILABLE");

            int delay = 0;
            for (BedCard c : bedCards) {
                c.play(delay);
                delay += 100;
            }

            cards.revalidate();
            cards.repaint();
        }

        private void addBedCard(String name, int free, int total) {
            BedCard panel = new BedCard();
            bedCards.add(panel);
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            panel.setBorder(new EmptyBorder(20, 20, 20, 20));

            JLabel n = new JLabel(name);
            n.setForeground(TEXT);
            n.setFont(new Font("SansSerif", Font.BOLD, 17));

            JLabel f = new JLabel(free + " beds available");
            f.setForeground(GREEN);
            f.setFont(new Font("SansSerif", Font.BOLD, 25));

            JLabel t = new JLabel("Total capacity: " + total);
            t.setForeground(MUTED);

            JProgressBar bar = new JProgressBar(0, total);
            bar.setValue(total - free);
            bar.setForeground(RED);
            bar.setBackground(new Color(45, 45, 45));

            JLabel click = new JLabel("CLICK FOR BED DETAILS ›");
            click.setForeground(RED);
            click.setFont(new Font("SansSerif", Font.BOLD, 10));

            panel.add(n);
            panel.add(Box.createVerticalStrut(12));
            panel.add(f);
            panel.add(Box.createVerticalStrut(7));
            panel.add(t);
            panel.add(Box.createVerticalStrut(10));
            panel.add(bar);
            panel.add(Box.createVerticalStrut(10));
            panel.add(click);

            panel.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    showBedDetails(name, free, total);
                }
            });

            cards.add(panel);
        }

        // Pop-up for one unit: a block per hospital with its free bed numbers.
        private void showBedDetails(String ward, int free, int total) {
            JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this),
                    "Bed Allocation - " + ward, Dialog.ModalityType.APPLICATION_MODAL);

            fitToScreen(dialog, 820, 640);
            dialog.setLocationRelativeTo(this);

            JPanel main = new JPanel(new BorderLayout(15, 15));
            main.setBackground(BG);
            main.setBorder(new EmptyBorder(20, 20, 20, 20));

            JLabel heading = new JLabel(ward + " • BEDS BY HOSPITAL");
            heading.setForeground(TEXT);
            heading.setFont(new Font("SansSerif", Font.BOLD, 24));
            main.add(heading, BorderLayout.NORTH);

            JPanel list = new JPanel();
            list.setBackground(BG);
            list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));

            int[][] split = WARD_SPLIT.get(ward);
            for (int i = 0; split != null && i < split.length && i < app.hospitals.size(); i++) {
                HospitalData hospital = app.hospitals.get(i);
                list.add(createHospitalBeds(ward, hospital.getName(), split[i][0], split[i][1], dialog));
                list.add(Box.createVerticalStrut(14));
            }

            JScrollPane scroll = new JScrollPane(list);
            scroll.setBorder(null);
            scroll.getVerticalScrollBar().setUnitIncrement(16);
            scroll.getViewport().setBackground(BG);
            main.add(scroll, BorderLayout.CENTER);

            JLabel info = new JLabel("Green = Available   Red = Occupied   Orange = Reserved");
            info.setForeground(MUTED);
            info.setHorizontalAlignment(SwingConstants.CENTER);
            main.add(info, BorderLayout.SOUTH);

            dialog.add(main);
            dialog.setVisible(true);
        }

        // One block per hospital: its name, which bed numbers of this unit are
        // free, and the bed tiles themselves.
        private JPanel createHospitalBeds(String ward, String hospitalName,
                                          int total, int free, JDialog dialog) {
            int occupied = Math.max(0, total - free);
            int reserved = Math.min(2, Math.max(0, free - 1));   // keep at least 1 bed available

            StringBuilder availableList = new StringBuilder();
            JPanel grid = new JPanel(new GridLayout(0, 5, 8, 8));
            grid.setOpaque(false);

            for (int i = 1; i <= total; i++) {
                String status;
                if (i <= occupied) status = "Occupied";
                else if (i <= occupied + reserved) status = "Reserved";
                else {
                    status = "Available";
                    if (availableList.length() > 0) availableList.append(", ");
                    availableList.append(i);
                }
                grid.add(createBedButton(ward, hospitalName, i, status, dialog));
            }

            JPanel box = new JPanel(new BorderLayout(0, 8));
            box.setBackground(PANEL);
            box.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(46, 53, 62)),
                    new EmptyBorder(12, 14, 14, 14)));
            box.setAlignmentX(Component.LEFT_ALIGNMENT);

            JLabel name = new JLabel(hospitalName + "  •  " + ward);
            name.setForeground(TEXT);
            name.setFont(new Font("SansSerif", Font.BOLD, 15));

            boolean any = availableList.length() > 0;
            JLabel avail = new JLabel(any
                    ? "Available beds: " + availableList + "   (" + (total - occupied - reserved)
                    + " of " + total + ")"
                    : "No beds available in this unit");
            avail.setForeground(any ? GREEN : RED);
            avail.setFont(new Font("SansSerif", Font.BOLD, 12));

            JPanel head = new JPanel();
            head.setOpaque(false);
            head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
            name.setAlignmentX(Component.LEFT_ALIGNMENT);
            avail.setAlignmentX(Component.LEFT_ALIGNMENT);
            head.add(name);
            head.add(Box.createVerticalStrut(3));
            head.add(avail);

            box.add(head, BorderLayout.NORTH);
            box.add(grid, BorderLayout.CENTER);
            box.setMaximumSize(new Dimension(Integer.MAX_VALUE, box.getPreferredSize().height));
            return box;
        }

        private JPanel createBedButton(String ward, String hospitalName, int bedNumber,
                                       String status, JDialog dialog) {
            JPanel bed = new JPanel(new BorderLayout());
            bed.setBorder(BorderFactory.createLineBorder(new Color(60, 65, 70)));
            bed.setPreferredSize(new Dimension(80, 52));

            JLabel number = new JLabel("Bed " + bedNumber, SwingConstants.CENTER);
            number.setForeground(TEXT);
            number.setFont(new Font("SansSerif", Font.BOLD, 13));

            JLabel statusLabel = new JLabel(status, SwingConstants.CENTER);
            statusLabel.setFont(new Font("SansSerif", Font.BOLD, 11));

            if (status.equals("Available")) {
                bed.setBackground(new Color(25, 70, 45));
                statusLabel.setForeground(GREEN);
            } else if (status.equals("Occupied")) {
                bed.setBackground(new Color(75, 25, 28));
                statusLabel.setForeground(RED);
            } else {
                bed.setBackground(new Color(75, 60, 25));
                statusLabel.setForeground(new Color(255, 190, 60));
            }

            bed.add(number, BorderLayout.NORTH);
            bed.add(statusLabel, BorderLayout.CENTER);
            bed.setToolTipText(hospitalName + " - " + ward + " - Bed " + bedNumber + " (" + status + ")");

            bed.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (status.equals("Available")) {
                        reserveBed(ward, hospitalName, bedNumber, dialog);
                    } else if (status.equals("Occupied")) {
                        showPatientAssignment(ward, hospitalName, bedNumber);
                    } else {
                        Dialogs.showMessageDialog(dialog,
                                "Bed " + bedNumber + " (" + ward + ", " + hospitalName
                                        + ") is already reserved.",
                                "Bed Status", Dialogs.INFORMATION_MESSAGE);
                    }
                }
            });

            return bed;
        }

        private void reserveBed(String ward, String hospitalName, int bedNumber, JDialog dialog) {
            int result = Dialogs.showConfirmDialog(dialog,
                    "Reserve Bed " + bedNumber + " in " + ward + "\nat " + hospitalName
                            + " for the incoming patient?",
                    "Direct Bed Reservation", Dialogs.YES_NO_OPTION);

            if (result == Dialogs.YES_OPTION) {
                Dialogs.showMessageDialog(dialog,
                        "Bed " + bedNumber + " has been marked RESERVED.\n\nHospital: " + hospitalName
                                + "\nUnit: " + ward,
                        "Bed Reserved", Dialogs.INFORMATION_MESSAGE);
            }
        }

        private void showPatientAssignment(String ward, String hospitalName, int bedNumber) {
            Dialogs.showMessageDialog(this,
                    "Hospital: " + hospitalName + "\nUnit: " + ward + "\nBed: " + bedNumber
                            + "\nStatus: Occupied\nAssigned patient details are confidential.",
                    "Patient Assignment", Dialogs.INFORMATION_MESSAGE);
        }

        // Called automatically when the ambulance reaches the selected hospital.
        // The user can select an available bed and treatment then starts.
        void showIncomingPatientBedSelection(EmergencyRequest request) {
            HospitalData selectedHospital = null;
            for (HospitalData h : app.hospitals) {
                if (h.getName().equals(request.getHospitalName())) {
                    selectedHospital = h;
                    break;
                }
            }

            if (selectedHospital == null) {
                Dialogs.showMessageDialog(this,
                        "Hospital arrival confirmed, but no bed record is available.",
                        "Hospital Arrival", Dialogs.WARNING_MESSAGE);
                return;
            }

            Dialogs.showMessageDialog(this,
                    "Ambulance has reached the selected hospital.\n\n"
                            + "Hospital: " + selectedHospital.getName() + "\n"
                            + "Patient: " + request.getName() + "\n\n"
                            + "Please select an available bed to start treatment.",
                    "Hospital Arrival Confirmed", Dialogs.INFORMATION_MESSAGE);

            showBedDetailsForHospital(request, selectedHospital);
        }

        private void showBedDetailsForHospital(EmergencyRequest request, HospitalData hospital) {
            String[] wards = {"Emergency Ward", "ICU", "CCU", "General Ward", "Pediatric", "Trauma Unit"};
            String[] availableWards = wards;

            JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this),
                    "Select Bed - " + hospital.getName(), Dialog.ModalityType.APPLICATION_MODAL);
            fitToScreen(dialog, 620, 430);
            dialog.setLocationRelativeTo(this);

            JPanel panel = new JPanel(new BorderLayout(12, 12));
            panel.setBackground(BG);
            panel.setBorder(new EmptyBorder(20, 20, 20, 20));

            JLabel heading = new JLabel("SELECT AN AVAILABLE BED");
            heading.setForeground(TEXT);
            heading.setFont(new Font("SansSerif", Font.BOLD, 22));
            panel.add(heading, BorderLayout.NORTH);

            JPanel choices = new JPanel(new GridLayout(2, 3, 12, 12));
            choices.setOpaque(false);
            for (String ward : availableWards) {
                int[][] split = WARD_SPLIT.get(ward);
                int availableBeds = 0;
                if (split != null) {
                    for (int i = 0; i < split.length && i < app.hospitals.size(); i++) {
                        if (app.hospitals.get(i).getName().equals(hospital.getName())) {
                            availableBeds = split[i][1];
                            break;
                        }
                    }
                }
                final int bedsAvailable = availableBeds;
                JButton b = new RoundedButton(ward + "\n" + bedsAvailable + " available", bedsAvailable > 0);
                b.addActionListener(e -> {
                    if (bedsAvailable <= 0) {
                        Dialogs.showMessageDialog(dialog, "No bed is currently available in " + ward + ".",
                                "No Bed Available", Dialogs.WARNING_MESSAGE);
                        return;
                    }
                    Dialogs.showMessageDialog(dialog,
                            "Bed selected in " + ward + " at " + hospital.getName() + "\n\n"
                                    + "Patient: " + request.getName() + "\n"
                                    + "Treatment has started.",
                            "Treatment Started", Dialogs.INFORMATION_MESSAGE);
                    dialog.dispose();
                });
                choices.add(b);
            }

            panel.add(choices, BorderLayout.CENTER);
            JLabel note = new JLabel("Only beds belonging to the selected hospital are shown.");
            note.setForeground(MUTED);
            note.setHorizontalAlignment(SwingConstants.CENTER);
            panel.add(note, BorderLayout.SOUTH);
            dialog.add(panel);
            dialog.setVisible(true);
        }
    }


    // =========================================================
    // HOSPITALS PAGE
    // INHERITANCE: HospitalsPage extends AppPage.
    // =========================================================
    static class HospitalsPage extends AppPage {

        private final JPanel list = new JPanel();
        private final List<AnimatedCard> hospitalCards = new ArrayList<AnimatedCard>();
        private boolean adjusting = false;

        private final JComboBox<String> distanceFilter = new JComboBox<String>(
                new String[]{"All Distances", "Under 3 km", "Under 5 km", "Under 8 km"});

        private final JComboBox<String> specialtyFilter = new JComboBox<String>(
                new String[]{"All Specialties", "Emergency", "ICU", "Trauma", "Pediatric"});

        HospitalsPage(Main app) {
            super(app);

            JPanel outer = new JPanel(new BorderLayout(15, 15));
            outer.setOpaque(false);
            outer.setBorder(new EmptyBorder(30, 45, 30, 45));

            JPanel heading = new JPanel(new BorderLayout());
            heading.setOpaque(false);
            heading.add(title("NEARBY HOSPITALS"), BorderLayout.WEST);

            JButton refresh = new RoundedButton("REFRESH", false);
            refresh.addActionListener(e -> {
                adjusting = true;
                distanceFilter.setSelectedIndex(0);
                specialtyFilter.setSelectedIndex(0);
                adjusting = false;
                refreshData();
            });
            heading.add(refresh, BorderLayout.EAST);
            styleCombo(distanceFilter);
            styleCombo(specialtyFilter);


            JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
            filters.setOpaque(false);
            filters.add(createFilterLabel("DISTANCE"));
            filters.add(distanceFilter);
            filters.add(createFilterLabel("SPECIALTY"));
            filters.add(specialtyFilter);

            distanceFilter.addActionListener(e -> { if (!adjusting) refreshData(); });
            specialtyFilter.addActionListener(e -> { if (!adjusting) refreshData(); });

            JPanel top = new JPanel(new BorderLayout(0, 12));
            top.setOpaque(false);
            top.add(heading, BorderLayout.NORTH);
            top.add(filters, BorderLayout.SOUTH);

            list.setOpaque(false);
            list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));

            JScrollPane scroll = new JScrollPane(list);
            scroll.setBorder(null);
            scroll.getViewport().setBackground(BG);

            outer.add(top, BorderLayout.NORTH);
            outer.add(scroll, BorderLayout.CENTER);
            add(outer, BorderLayout.CENTER);

            refreshData();
        }

        private JLabel createFilterLabel(String text) {
            JLabel label = new JLabel(text);
            label.setForeground(MUTED);
            label.setFont(new Font("SansSerif", Font.BOLD, 10));
            return label;
        }
        private void styleCombo(JComboBox<String> box) {
            final Color field = new Color(21, 26, 33);
            final Color border = new Color(55, 60, 65);
            final Color highlight = new Color(56, 26, 30);

            box.setUI(new javax.swing.plaf.basic.BasicComboBoxUI() {

                // custom arrow button: dark square with a red triangle
                @Override
                protected JButton createArrowButton() {
                    JButton b = new JButton() {
                        @Override
                        protected void paintComponent(Graphics g) {
                            Graphics2D g2 = (Graphics2D) g.create();
                            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                    RenderingHints.VALUE_ANTIALIAS_ON);
                            g2.setColor(getModel().isPressed() ? highlight : field);
                            g2.fillRect(0, 0, getWidth(), getHeight());
                            g2.setColor(RED);
                            int cx = getWidth() / 2, cy = getHeight() / 2;
                            g2.fillPolygon(new int[]{cx - 5, cx + 5, cx},
                                    new int[]{cy - 2, cy - 2, cy + 3}, 3);
                            g2.dispose();
                        }
                    };
                    b.setBorder(BorderFactory.createEmptyBorder());
                    b.setContentAreaFilled(false);
                    b.setFocusPainted(false);
                    b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    return b;
                }

                // background behind the currently selected value
                @Override
                public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean hasFocus) {
                    g.setColor(field);
                    g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
                }

                // dark popup list with a thin red border
                @Override
                protected javax.swing.plaf.basic.ComboPopup createPopup() {
                    javax.swing.plaf.basic.BasicComboPopup popup =
                            new javax.swing.plaf.basic.BasicComboPopup(comboBox);
                    popup.setBorder(BorderFactory.createLineBorder(new Color(245, 45, 45, 150)));
                    popup.getList().setBackground(field);
                    popup.getList().setSelectionBackground(highlight);
                    popup.getList().setSelectionForeground(TEXT);
                    return popup;
                }
            });

            box.setBackground(field);
            box.setForeground(TEXT);
            box.setFont(new Font("SansSerif", Font.BOLD, 12));
            box.setFocusable(false);
            box.setBorder(BorderFactory.createLineBorder(border));
            box.setPreferredSize(new Dimension(155, 34));

            // how each item (and the shown value) is drawn
            box.setRenderer(new DefaultListCellRenderer() {
                @Override
                public Component getListCellRendererComponent(JList<?> list, Object value,
                                                              int index, boolean selected, boolean focus) {
                    JLabel l = (JLabel) super.getListCellRendererComponent(
                            list, value, index, false, false);
                    l.setOpaque(true);
                    l.setBackground(selected ? highlight : field);
                    l.setForeground(TEXT);
                    l.setFont(new Font("SansSerif", Font.PLAIN, 12));
                    l.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
                    return l;
                }
            });
        }


        // POLYMORPHISM: HospitalsPage's own refreshData(). It rebuilds the
        // hospital list using the selected distance and specialty filters.
        @Override
        void refreshData() {
            list.removeAll();
            hospitalCards.clear();

            String distance = distanceFilter.getSelectedItem().toString();
            String specialty = specialtyFilter.getSelectedItem().toString();

            for (HospitalData hospital : app.hospitals) {
                if (!matchesDistance(hospital, distance)) continue;
                if (!matchesSpecialty(hospital, specialty)) continue;
                addHospital(hospital);
            }

            int delay = 0;
            for (AnimatedCard c : hospitalCards) {
                c.play(delay);
                delay += 120;
            }

            list.revalidate();
            list.repaint();
        }

        private boolean matchesDistance(HospitalData hospital, String filter) {
            if (filter.equals("All Distances")) return true;
            if (filter.equals("Under 3 km")) return hospital.getDistance() <= 3;
            if (filter.equals("Under 5 km")) return hospital.getDistance() <= 5;
            return hospital.getDistance() <= 8;
        }

        private boolean matchesSpecialty(HospitalData hospital, String filter) {
            if (filter.equals("All Specialties")) return true;
            return hospital.getSpecialties().toLowerCase().contains(filter.toLowerCase());
        }

        private void addHospital(HospitalData hospital) {
            AnimatedCard card = new AnimatedCard();
            hospitalCards.add(card);
            card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 125));
            card.setBorder(new EmptyBorder(15, 20, 15, 20));
            card.setLayout(new BorderLayout(15, 0));

            JPanel middle = new JPanel();
            middle.setOpaque(false);
            middle.setLayout(new BoxLayout(middle, BoxLayout.Y_AXIS));

            JLabel n = new JLabel(hospital.getName());
            n.setForeground(TEXT);
            n.setFont(new Font("SansSerif", Font.BOLD, 17));

            JLabel s = new JLabel(hospital.getSpecialties());
            s.setForeground(MUTED);

            JLabel b = createBedLabel(hospital);

            middle.add(n);
            middle.add(Box.createVerticalStrut(6));
            middle.add(s);
            middle.add(Box.createVerticalStrut(5));
            middle.add(b);

            JPanel right = new JPanel();
            right.setOpaque(false);
            right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));

            JLabel d = new JLabel(String.format("%.1f km", hospital.getDistance()));
            d.setForeground(TEXT);
            d.setFont(new Font("SansSerif", Font.BOLD, 15));

            JButton call = new RoundedButton("CALL DESK", true);
            call.addActionListener(e -> showEmergencyDesk(hospital));

            right.add(d);
            right.add(Box.createVerticalStrut(8));
            right.add(call);

            card.add(middle, BorderLayout.CENTER);
            card.add(right, BorderLayout.EAST);

            MouseAdapter cardClick = new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getSource() instanceof JButton) return;
                    showHospitalDetails(hospital);
                }
            };

            card.addMouseListener(cardClick);
            middle.addMouseListener(cardClick);
            n.addMouseListener(cardClick);
            s.addMouseListener(cardClick);
            b.addMouseListener(cardClick);
            d.addMouseListener(cardClick);

            list.add(card);
            list.add(Box.createVerticalStrut(10));
        }

        private JLabel createBedLabel(HospitalData hospital) {
            int available = hospital.getAvailableBeds();
            int total = hospital.getTotalBeds();
            JLabel bedLabel;

            if (available <= 0) {
                bedLabel = new JLabel("NO BEDS AVAILABLE");
                bedLabel.setForeground(RED);
            } else if (available <= total * 0.20) {
                bedLabel = new JLabel(available + " beds available - LIMITED");
                bedLabel.setForeground(new Color(255, 170, 0));
            } else {
                bedLabel = new JLabel(available + " beds available / " + total);
                bedLabel.setForeground(GREEN);
            }
            return bedLabel;
        }

        private void showHospitalDetails(HospitalData hospital) {
            JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this),
                    "Hospital Details", Dialog.ModalityType.APPLICATION_MODAL);

            fitToScreen(dialog, 1000, 720);
            dialog.setLocationRelativeTo(this);

            JPanel main = new JPanel(new BorderLayout(0, 15));
            main.setBackground(BG);
            main.setBorder(new EmptyBorder(20, 20, 20, 20));

            main.add(createHospitalImage(hospital, 940, 380), BorderLayout.NORTH);

            JPanel details = new JPanel();
            details.setOpaque(false);
            details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));

            JLabel name = new JLabel(hospital.getName());
            name.setForeground(TEXT);
            name.setFont(new Font("SansSerif", Font.BOLD, 24));

            JLabel specialty = new JLabel("Specialties: " + hospital.getSpecialties());
            specialty.setForeground(MUTED);

            JLabel address = new JLabel("Address: " + getHospitalAddress(hospital));
            address.setForeground(TEXT);

            JLabel phone = new JLabel("Emergency: " + hospital.getPhone());
            phone.setForeground(TEXT);

            JLabel beds = new JLabel("Beds: " + hospital.getAvailableBeds()
                    + " available / " + hospital.getTotalBeds());
            beds.setForeground(GREEN);

            JLabel team = new JLabel("On-Duty Team: " + getHospitalTeam(hospital));
            team.setForeground(TEXT);

            details.add(name);
            details.add(Box.createVerticalStrut(8));
            details.add(specialty);
            details.add(Box.createVerticalStrut(7));
            details.add(address);
            details.add(Box.createVerticalStrut(7));
            details.add(phone);
            details.add(Box.createVerticalStrut(7));
            details.add(beds);
            details.add(Box.createVerticalStrut(7));
            details.add(team);

            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            buttons.setOpaque(false);

            JButton directions = new RoundedButton("GET DIRECTIONS", true);
            directions.addActionListener(e -> Dialogs.showMessageDialog(dialog,
                    "Route to " + hospital.getName() + ":\n" + getHospitalAddress(hospital),
                    "Navigation", Dialogs.INFORMATION_MESSAGE));

            JButton call = new RoundedButton("CALL EMERGENCY DESK", true);
            call.addActionListener(e -> showEmergencyDesk(hospital));

            JButton close = new RoundedButton("CLOSE", false);
            close.addActionListener(e -> dialog.dispose());

            buttons.add(directions);
            buttons.add(call);
            buttons.add(close);

            JPanel bottom = new JPanel(new BorderLayout());
            bottom.setOpaque(false);
            bottom.add(details, BorderLayout.CENTER);
            bottom.add(buttons, BorderLayout.SOUTH);

            main.add(bottom, BorderLayout.CENTER);
            dialog.setContentPane(main);
            dialog.setVisible(true);
        }

        private void showEmergencyDesk(HospitalData hospital) {
            JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this),
                    "Emergency Desk", Dialog.ModalityType.APPLICATION_MODAL);

            fitToScreen(dialog, 800, 620);
            dialog.setLocationRelativeTo(this);

            Color orange = new Color(255, 140, 0);

            JPanel main = new JPanel(new BorderLayout());
            main.setBackground(orange);
            main.setBorder(new EmptyBorder(18, 18, 18, 18));

            main.add(createHospitalImage(hospital, 740, 330), BorderLayout.NORTH);

            JPanel info = new JPanel();
            info.setBackground(orange);
            info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));

            JLabel title = new JLabel("EMERGENCY DESK");
            title.setForeground(Color.WHITE);
            title.setFont(new Font("SansSerif", Font.BOLD, 24));

            JLabel hospitalName = new JLabel(hospital.getName());
            hospitalName.setForeground(Color.WHITE);
            hospitalName.setFont(new Font("SansSerif", Font.BOLD, 18));

            JLabel phone = new JLabel("CALL: " + hospital.getPhone());
            phone.setForeground(Color.WHITE);
            phone.setFont(new Font("SansSerif", Font.BOLD, 20));

            JLabel address = new JLabel(getHospitalAddress(hospital));
            address.setForeground(Color.WHITE);

            info.add(title);
            info.add(Box.createVerticalStrut(8));
            info.add(hospitalName);
            info.add(Box.createVerticalStrut(8));
            info.add(phone);
            info.add(Box.createVerticalStrut(8));
            info.add(address);

            main.add(info, BorderLayout.CENTER);

            JButton close = new RoundedButton("CLOSE", true);
            close.addActionListener(e -> dialog.dispose());

            JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            bottom.setBackground(orange);
            bottom.add(close);

            main.add(bottom, BorderLayout.SOUTH);
            dialog.setContentPane(main);
            dialog.setVisible(true);
        }

        // FILE HANDLING: loads a hospital picture from the images folder. If
        // the file is missing (width <= 0) a text placeholder is shown instead
        // of crashing.
        private JLabel createHospitalImage(HospitalData hospital, int width, int height) {
            ImageIcon original = new ImageIcon(Main.resolve(getHospitalImagePath(hospital)).getPath());

            JLabel imageLabel = new JLabel();
            imageLabel.setHorizontalAlignment(SwingConstants.CENTER);
            imageLabel.setVerticalAlignment(SwingConstants.CENTER);

            if (original.getIconWidth() > 0) {
                int iw = original.getIconWidth();
                int ih = original.getIconHeight();
                double scale = Math.min((double) width / iw, (double) height / ih);
                int nw = (int) (iw * scale);
                int nh = (int) (ih * scale);
                Image image = original.getImage().getScaledInstance(nw, nh, Image.SCALE_SMOOTH);
                imageLabel.setIcon(new ImageIcon(image));
            } else {
                imageLabel.setText("HOSPITAL IMAGE");
                imageLabel.setForeground(Color.WHITE);
                imageLabel.setBackground(new Color(40, 45, 50));
                imageLabel.setOpaque(true);
                imageLabel.setPreferredSize(new Dimension(width, height));
            }
            return imageLabel;
        }

        private String getHospitalImagePath(HospitalData hospital) {
            String name = hospital.getName().toLowerCase();
            if (name.contains("city emergency")) return "images/city_emergency.png";
            if (name.contains("medicare")) return "images/medicare_central.png";
            if (name.contains("lifeline")) return "images/lifeline.png";
            if (name.contains("prime health")) return "images/prime_health.png";
            return "images/hospital.jpg";
        }

        private String getHospitalAddress(HospitalData hospital) {
            String name = hospital.getName().toLowerCase();
            if (name.contains("city emergency")) return "Main Boulevard, Block 4, City Center";
            if (name.contains("medicare")) return "Healthcare Avenue, Phase 2";
            if (name.contains("lifeline")) return "University Road, Sector 7";
            if (name.contains("prime health")) return "Hospital Road, Block 12";
            return "Address not available";
        }

        private String getHospitalTeam(HospitalData hospital) {
            String name = hospital.getName().toLowerCase();
            if (name.contains("city emergency")) return "Dr. Ayesha Khan, Dr. Bilal Ahmed";
            if (name.contains("medicare")) return "Dr. Sara Malik, Dr. Usman Ali";
            if (name.contains("lifeline")) return "Dr. Hina Raza, Dr. Farhan Siddiqui";
            if (name.contains("prime health")) return "Dr. Nadia Hussain, Dr. Omar Sheikh";
            return "General Duty Team";
        }
    }
    // =========================================================
// SERVICE ICON (drawn, no emoji fonts needed)
// INHERITANCE: extends JComponent.
// =========================================================
    static class ServiceIcon extends JComponent {

        private final String type;

        ServiceIcon(String type) {
            this.type = type;
            setPreferredSize(new Dimension(36, 36));
            setMaximumSize(new Dimension(36, 36));
            setAlignmentX(Component.LEFT_ALIGNMENT);
        }

        // POLYMORPHISM: overrides paintComponent(). The same method draws a
        // different icon depending on the "type" given to the object.
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(RED);
            g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

            switch (type) {
                case "dispatch":
                    g2.drawOval(2, 2, 30, 30);
                    g2.fillRect(15, 8, 4, 18);
                    g2.fillRect(8, 15, 18, 4);
                    break;
                case "route":
                    g2.fillOval(2, 24, 8, 8);
                    g2.fillOval(24, 2, 8, 8);
                    Path2D.Double p = new Path2D.Double();
                    p.moveTo(6, 28);
                    p.curveTo(6, 12, 28, 26, 28, 6);
                    g2.draw(p);
                    break;
                case "telecare":
                    g2.drawPolyline(new int[]{2, 10, 14, 20, 24, 34},
                            new int[]{18, 18, 6, 30, 18, 18}, 6);
                    break;
                case "beds":
                    g2.fillRoundRect(2, 18, 30, 6, 3, 3);
                    g2.drawLine(2, 10, 2, 28);
                    g2.drawLine(32, 24, 32, 28);
                    g2.fillOval(6, 11, 7, 7);
                    break;
                case "hospital":
                    g2.drawRect(5, 8, 24, 22);
                    g2.fillRect(15, 12, 4, 12);
                    g2.fillRect(11, 16, 12, 4);
                    break;
                case "records":
                    g2.drawRoundRect(7, 3, 20, 28, 4, 4);
                    g2.drawLine(12, 12, 22, 12);
                    g2.drawLine(12, 18, 22, 18);
                    g2.drawLine(12, 24, 19, 24);
                    break;
                default:
                    g2.drawOval(2, 2, 30, 30);
            }
            g2.dispose();
        }
    }
    // =========================================================
// SERVICES PAGE
// INHERITANCE: ServicesPage extends AppPage.
// =========================================================
    static class ServicesPage extends AppPage {

        private final JPanel grid = new JPanel(new GridLayout(2, 3, 15, 15));
        private final List<AnimatedCard> serviceCards = new ArrayList<AnimatedCard>();

        ServicesPage(Main app) {
            super(app);

            JPanel outer = new JPanel(new BorderLayout(15, 15));
            outer.setOpaque(false);
            outer.setBorder(new EmptyBorder(30, 45, 30, 45));

            JPanel top = new JPanel();
            top.setOpaque(false);
            top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
            top.add(title("OUR SERVICES"));
            top.add(Box.createVerticalStrut(7));
            top.add(subtitle("Everything MediRoute provides to get patients to care faster."));

            grid.setOpaque(false);

            addService("dispatch", "EMERGENCY DISPATCH",
                    "Request an ambulance in seconds. The nearest free unit is assigned automatically with priority handling for critical cases.",
                    "Emergency");
            addService("route", "SMART ROUTING & TRACKING",
                    "Live traffic-aware routes and real-time ambulance tracking from pickup to hospital arrival.",
                    "Tracking");
            addService("beds", "BED AVAILABILITY",
                    "Real-time bed status across Emergency, ICU, CCU, Trauma, Pediatric and General wards, with direct bed reservation.",
                    "Beds");
            addService("hospitals", "HOSPITAL NETWORK",
                    "Browse nearby hospitals by distance and specialty, view details, get directions and call the emergency desk.",
                    "Hospitals");
            addService("records", "DISPATCH RECORDS",
                    "Every request is saved automatically. Filter the history by priority and review past dispatches.",
                    "History");
            addService("treatment", "IN-AMBULANCE TREATMENT",
                    "Mini-hospital support during transport with treatment status updates before hospital arrival.",
                    "Tracking");

            outer.add(top, BorderLayout.NORTH);
            outer.add(grid, BorderLayout.CENTER);
            add(outer, BorderLayout.CENTER);
        }

        private void addService(String icon, String name, String description, final String targetPage) {
            AnimatedCard card = new AnimatedCard();
            serviceCards.add(card);
            card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
            card.setBorder(new EmptyBorder(20, 22, 20, 22));
            card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            ServiceIcon ic = new ServiceIcon(icon);

            JLabel n = new JLabel(name);
            n.setForeground(TEXT);
            n.setFont(new Font("SansSerif", Font.BOLD, 16));

            JLabel d = new JLabel("<html><div style='width:210px'>" + description + "</div></html>");
            d.setForeground(MUTED);
            d.setFont(new Font("SansSerif", Font.PLAIN, 12));

            JLabel open = new JLabel("OPEN ›");
            open.setForeground(RED);
            open.setFont(new Font("SansSerif", Font.BOLD, 10));

            card.add(ic);
            card.add(Box.createVerticalStrut(10));
            card.add(n);
            card.add(Box.createVerticalStrut(8));
            card.add(d);
            card.add(Box.createVerticalGlue());
            card.add(open);

            card.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    app.showPage(targetPage);
                }
            });

            grid.add(card);
        }

        // POLYMORPHISM: ServicesPage's own refreshData(). It replays the
        // card animations.
        @Override
        void refreshData() {
            int delay = 0;
            for (AnimatedCard c : serviceCards) {
                c.play(delay);
                delay += 100;
            }
        }
    }

    // =========================================================
// WIDTH TRACKING PANEL (keeps scroll content the same width as the window)
// INHERITANCE: extends JPanel and implements the Scrollable interface.
// POLYMORPHISM: implements the interface methods so a JScrollPane can treat
// this panel as any other Scrollable.
// =========================================================
    static class WidthTrackingPanel extends JPanel implements Scrollable {
        public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 18; }
        public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return 100; }
        public boolean getScrollableTracksViewportWidth() { return true; }
        public boolean getScrollableTracksViewportHeight() { return false; }
    }

    // =========================================================
    // ABOUT PAGE
    // INHERITANCE: AboutPage extends AppPage.
    // =========================================================
    static class AboutPage extends AppPage {

        // ---- TEAM MEMBERS: {name, role, image file} ----
        private static final String[][] PARTNERS = {
                {"Ayesha Nadeem Khan", "Team Member", "images/partner_1.png"},
                {"Alishba Sohail", "Team Member", "images/partner_2.png"},
                {"Sanyya Hassan", "Team Member", "images/partner_3.png"},
                {"Fatima Shahid", "Team Member", "images/partner_4.png"}
        };
        private static final Color[] PARTNER_TINTS = {
                new Color(120, 70, 190), new Color(200, 70, 130),
                new Color(40, 100, 190), new Color(200, 150, 30)
        };

        private final List<AnimatedCard> cards = new ArrayList<AnimatedCard>();

        AboutPage(Main app) {
            super(app);

            JPanel content = new WidthTrackingPanel();
            content.setOpaque(false);
            content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
            content.setBorder(new EmptyBorder(36, 64, 44, 64));

            // ---------- WHO WE ARE (text left, 4 clickable cards right) ----------
            JPanel hero = new JPanel(new GridLayout(1, 2, 40, 0));
            hero.setOpaque(false);
            hero.setPreferredSize(new Dimension(100, 270));
            hero.setMaximumSize(new Dimension(Integer.MAX_VALUE, 270));
            hero.setAlignmentX(Component.LEFT_ALIGNMENT);

            JPanel heroText = new JPanel();
            heroText.setOpaque(false);
            heroText.setLayout(new BoxLayout(heroText, BoxLayout.Y_AXIS));

            JLabel tag = new JLabel("  WHO WE ARE  ");
            tag.setForeground(TEXT);
            tag.setFont(new Font("SansSerif", Font.BOLD, 11));
            tag.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(RED, 1, true), new EmptyBorder(5, 8, 5, 8)));
            tag.setAlignmentX(Component.LEFT_ALIGNMENT);

            JLabel heading = new JLabel("<html>About <span style='color:#F52D2D'>MediRoute</span></html>");
            heading.setForeground(TEXT);
            heading.setFont(new Font("Serif", Font.BOLD, 44));
            heading.setAlignmentX(Component.LEFT_ALIGNMENT);

            JLabel intro = new JLabel("<html><div style='width:340px'>MediRoute is an Ambulance Management System. "
                    + "It connects patients, ambulances and hospitals in one network: request an ambulance, "
                    + "get the nearest free unit with emergency priority, track it live, find a hospital bed "
                    + "and keep every dispatch record.</div></html>");
            intro.setForeground(MUTED);
            intro.setFont(new Font("SansSerif", Font.PLAIN, 14));
            intro.setAlignmentX(Component.LEFT_ALIGNMENT);

            heroText.add(Box.createVerticalGlue());
            heroText.add(tag);
            heroText.add(Box.createVerticalStrut(12));
            heroText.add(heading);
            heroText.add(Box.createVerticalStrut(10));
            heroText.add(intro);
            heroText.add(Box.createVerticalGlue());

            JPanel heroCards = new JPanel(new GridLayout(2, 2, 14, 14));
            heroCards.setOpaque(false);
            heroCards.add(miniCard("dispatch", "Emergency", "Emergency"));
            heroCards.add(miniCard("route", "Tracking", "Tracking"));
            heroCards.add(miniCard("hospital", "Hospitals", "Hospitals"));
            heroCards.add(miniCard("records", "History", "History"));

            hero.add(heroText);
            hero.add(heroCards);
            content.add(hero);
            content.add(Box.createVerticalStrut(44));

            // ---------- WHAT WE DO ----------
            content.add(centerHeader("WHAT WE DO", "Tap a card to open the matching page."));
            content.add(Box.createVerticalStrut(16));
            JPanel what = new JPanel(new GridLayout(2, 3, 16, 16));
            what.setOpaque(false);
            what.setPreferredSize(new Dimension(100, 330));
            what.setMaximumSize(new Dimension(Integer.MAX_VALUE, 330));
            what.setAlignmentX(Component.LEFT_ALIGNMENT);
            what.add(doCard("dispatch", "Emergency Dispatch", "Request an ambulance in seconds. The nearest free unit is assigned.", "Emergency"));
            what.add(doCard("route", "Live Tracking", "Traffic-aware routes and real-time ambulance tracking.", "Tracking"));
            what.add(doCard("beds", "Bed Availability", "Live bed status across Emergency, ICU, CCU and more.", "Beds"));
            what.add(doCard("hospital", "Hospital Network", "Nearby hospitals by distance and specialty with directions.", "Hospitals"));
            what.add(doCard("records", "Dispatch History", "Every request is saved. Filter and review past dispatches.", "History"));
            what.add(doCard("telecare", "Our Services", "Everything MediRoute offers, including in-ambulance care.", "Services"));
            content.add(what);
            content.add(Box.createVerticalStrut(44));

            // ---------- MISSION & VISION ----------
            content.add(centerHeader("MISSION AND VISION", null));
            content.add(Box.createVerticalStrut(16));
            JPanel mv = row(2, 130, 18);
            mv.add(infoCard("MISSION", "Make every journey smooth and reach every patient faster, keeping emergency response time to a minimum."));
            mv.add(infoCard("VISION", "A city where ambulances, hospitals and doctors work as one network and no patient waits for help."));
            content.add(mv);
            content.add(Box.createVerticalStrut(44));

            // ---------- MEET OUR TEAM ----------
            content.add(centerHeader("MEET OUR TEAM", "The people behind the project."));
            content.add(Box.createVerticalStrut(16));
            JPanel partners = row(4, 168, 18);
            for (int i = 0; i < PARTNERS.length; i++) {
                partners.add(partnerCard(PARTNERS[i][0], PARTNERS[i][1], PARTNERS[i][2],
                        PARTNER_TINTS[i % PARTNER_TINTS.length]));
            }
            content.add(partners);

            JScrollPane scroll = new JScrollPane(content);
            scroll.setBorder(null);
            scroll.setOpaque(false);
            scroll.getViewport().setOpaque(false);
            scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            scroll.getVerticalScrollBar().setUnitIncrement(18);
            add(scroll, BorderLayout.CENTER);
        }

        // ---------------------------------------------------------
        // NEW HELPERS (clickable cards, centered headers)
        // ---------------------------------------------------------
        private void makeClickable(AnimatedCard card, final String targetPage) {
            card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            card.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) { app.showPage(targetPage); }
            });
        }

        private AnimatedCard miniCard(String icon, String label, String targetPage) {
            AnimatedCard card = new AnimatedCard();
            cards.add(card);
            card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
            card.setBorder(new EmptyBorder(14, 10, 14, 10));
            ServiceIcon ic = new ServiceIcon(icon);
            ic.setAlignmentX(Component.CENTER_ALIGNMENT);
            JLabel l = new JLabel(label);
            l.setForeground(TEXT);
            l.setFont(new Font("SansSerif", Font.BOLD, 13));
            l.setAlignmentX(Component.CENTER_ALIGNMENT);
            card.add(Box.createVerticalGlue());
            card.add(ic);
            card.add(Box.createVerticalStrut(10));
            card.add(l);
            card.add(Box.createVerticalGlue());
            makeClickable(card, targetPage);
            return card;
        }

        private AnimatedCard doCard(String icon, String name, String desc, String targetPage) {
            AnimatedCard card = new AnimatedCard();
            cards.add(card);
            card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
            card.setBorder(new EmptyBorder(18, 16, 18, 16));
            ServiceIcon ic = new ServiceIcon(icon);
            ic.setAlignmentX(Component.CENTER_ALIGNMENT);
            JLabel n = new JLabel(name);
            n.setForeground(TEXT);
            n.setFont(new Font("Serif", Font.BOLD, 17));
            n.setAlignmentX(Component.CENTER_ALIGNMENT);
            JLabel d = new JLabel("<html><div style='width:180px;text-align:center'>" + desc + "</div></html>");
            d.setForeground(MUTED);
            d.setFont(new Font("SansSerif", Font.PLAIN, 12));
            d.setAlignmentX(Component.CENTER_ALIGNMENT);
            card.add(Box.createVerticalGlue());
            card.add(ic);
            card.add(Box.createVerticalStrut(10));
            card.add(n);
            card.add(Box.createVerticalStrut(6));
            card.add(d);
            card.add(Box.createVerticalGlue());
            makeClickable(card, targetPage);
            return card;
        }

        private JPanel centerHeader(String title, String subtitle) {
            JPanel p = new JPanel();
            p.setOpaque(false);
            p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
            p.setAlignmentX(Component.LEFT_ALIGNMENT);
            JLabel t = new JLabel(title);
            t.setForeground(TEXT);
            t.setFont(new Font("Serif", Font.BOLD, 28));
            t.setAlignmentX(Component.CENTER_ALIGNMENT);
            p.add(t);
            p.add(Box.createVerticalStrut(6));
            JLabel line = new JLabel(" ") {
                @Override
                protected void paintComponent(Graphics g) {
                    g.setColor(RED);
                    g.fillRect((getWidth() - 60) / 2, 0, 60, 2);
                }
            };
            line.setPreferredSize(new Dimension(60, 3));
            line.setMaximumSize(new Dimension(Integer.MAX_VALUE, 3));
            line.setAlignmentX(Component.CENTER_ALIGNMENT);
            p.add(line);
            if (subtitle != null) {
                p.add(Box.createVerticalStrut(8));
                JLabel s = new JLabel(subtitle);
                s.setForeground(MUTED);
                s.setFont(new Font("SansSerif", Font.PLAIN, 12));
                s.setAlignmentX(Component.CENTER_ALIGNMENT);
                p.add(s);
            }
            p.setMaximumSize(new Dimension(Integer.MAX_VALUE, p.getPreferredSize().height));
            return p;
        }

        // ---------------------------------------------------------
        // LAYOUT HELPERS
        // ---------------------------------------------------------
        private JPanel row(int cols, int height, int gap) {
            JPanel p = new JPanel(new GridLayout(1, cols, gap, 0));
            p.setOpaque(false);
            p.setPreferredSize(new Dimension(100, height));
            p.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
            p.setAlignmentX(Component.LEFT_ALIGNMENT);
            return p;
        }

        private JPanel sectionHeader(String title, String subtitle) {
            JPanel p = new JPanel();
            p.setOpaque(false);
            p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
            p.setAlignmentX(Component.LEFT_ALIGNMENT);

            JLabel t = new JLabel(title);
            t.setForeground(TEXT);
            t.setFont(new Font("SansSerif", Font.BOLD, 17));
            t.setAlignmentX(Component.LEFT_ALIGNMENT);
            p.add(t);

            if (subtitle != null) {
                JLabel s = new JLabel(subtitle);
                s.setForeground(MUTED);
                s.setFont(new Font("SansSerif", Font.PLAIN, 12));
                s.setAlignmentX(Component.LEFT_ALIGNMENT);
                p.add(Box.createVerticalStrut(4));
                p.add(s);
            }
            p.setMaximumSize(new Dimension(Integer.MAX_VALUE, p.getPreferredSize().height));
            return p;
        }

        private String step(String number, String text) {
            return "<p style='margin-top:0; margin-bottom:7px'><b style='color:#F52D2D'>" + number
                    + "</b>&nbsp;&nbsp;" + text + "</p>";
        }

        // ---------------------------------------------------------
        // CARDS
        // ---------------------------------------------------------
        private AnimatedCard statCard(String value, String label) {
            AnimatedCard card = new AnimatedCard();
            cards.add(card);
            card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
            card.setBorder(new EmptyBorder(16, 24, 16, 24));

            JLabel v = new JLabel(value);
            v.setForeground(TEXT);
            v.setFont(new Font("SansSerif", Font.BOLD, 28));

            JLabel l = new JLabel(label);
            l.setForeground(MUTED);
            l.setFont(new Font("SansSerif", Font.BOLD, 11));

            card.add(Box.createVerticalGlue());
            card.add(v);
            card.add(Box.createVerticalStrut(3));
            card.add(l);
            card.add(Box.createVerticalGlue());
            return card;
        }

        private AnimatedCard infoCard(String head, String bodyHtml) {
            AnimatedCard card = new AnimatedCard();
            cards.add(card);
            card.setLayout(new BorderLayout(0, 10));
            card.setBorder(new EmptyBorder(20, 26, 18, 26));

            JLabel h = new JLabel(head);
            h.setForeground(RED);
            h.setFont(new Font("SansSerif", Font.BOLD, 14));

            JEditorPane body = new JEditorPane();
            body.setContentType("text/html");
            body.setEditable(false);
            body.setOpaque(false);
            body.setFocusable(false);
            body.setBorder(null);
            body.setText("<html><body style='font-family:SansSerif; font-size:13px; color:#B9BFC6'>"
                    + bodyHtml + "</body></html>");
            body.setCaretPosition(0);

            card.add(h, BorderLayout.NORTH);
            card.add(body, BorderLayout.CENTER);
            return card;
        }

        private AnimatedCard partnerCard(String name, String role, String imagePath, Color tint) {
            AnimatedCard card = new AnimatedCard();
            cards.add(card);
            card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
            card.setBorder(new EmptyBorder(22, 14, 18, 14));

            PartnerLogo logo = new PartnerLogo(name, imagePath, tint);

            JLabel n = new JLabel(name);
            n.setForeground(TEXT);
            n.setFont(new Font("SansSerif", Font.BOLD, 14));
            n.setAlignmentX(Component.CENTER_ALIGNMENT);

            JLabel r = new JLabel(role);
            r.setForeground(MUTED);
            r.setFont(new Font("SansSerif", Font.PLAIN, 11));
            r.setAlignmentX(Component.CENTER_ALIGNMENT);

            card.add(Box.createVerticalGlue());
            card.add(logo);
            card.add(Box.createVerticalStrut(14));
            card.add(n);
            card.add(Box.createVerticalStrut(3));
            card.add(r);
            card.add(Box.createVerticalGlue());
            return card;
        }

        // POLYMORPHISM: AboutPage's own refreshData(). It replays the card
        // animations.
        @Override
        void refreshData() {
            int delay = 0;
            for (AnimatedCard c : cards) {
                c.play(delay);
                delay += 70;
            }
        }

        // FILE HANDLING + EXCEPTION HANDLING: reads an image file from disk.
        // Returns null if the file is missing or cannot be read, so the
        // caller can show a placeholder instead.
        private static BufferedImage loadImage(String path) {
            try {
                File f = Main.resolve(path);
                if (!f.exists()) return null;
                return ImageIO.read(f);
            } catch (Exception ex) {
                return null;
            }
        }

        // =====================================================
        // PARTNER LOGO (round image, or initials badge if no image)
        // INHERITANCE: extends JComponent.
        // =====================================================
        static class PartnerLogo extends JComponent {

            private final String initials;
            private final Color tint;
            private final BufferedImage image;

            PartnerLogo(String name, String imagePath, Color tint) {
                this.tint = tint;
                this.image = loadImage(imagePath);

                String[] parts = name.replace("Dr. ", "").trim().split("\\s+");
                this.initials = parts.length > 1
                        ? ("" + parts[0].charAt(0) + parts[1].charAt(0)).toUpperCase()
                        : ("" + parts[0].charAt(0)).toUpperCase();

                Dimension size = new Dimension(68, 68);
                setPreferredSize(size);
                setMinimumSize(size);
                setMaximumSize(size);
                setAlignmentX(Component.CENTER_ALIGNMENT);
            }

            // POLYMORPHISM: overrides paintComponent().
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

                int d = Math.min(getWidth(), getHeight()) - 4;
                int x = (getWidth() - d) / 2, y = (getHeight() - d) / 2;
                Shape circle = new Ellipse2D.Double(x, y, d, d);

                Shape oldClip = g2.getClip();
                g2.clip(circle);
                if (image != null && image.getWidth() > 0 && image.getHeight() > 0) {
                    double scale = Math.max((double) d / image.getWidth(), (double) d / image.getHeight());
                    int dw = (int) (image.getWidth() * scale), dh = (int) (image.getHeight() * scale);
                    g2.drawImage(image, x + (d - dw) / 2, y + (d - dh) / 2, dw, dh, null);
                } else {
                    g2.setPaint(new GradientPaint(x, y, tint, x + d, y + d, new Color(10, 14, 18)));
                    g2.fillRect(x, y, d, d);
                    g2.setColor(Color.WHITE);
                    g2.setFont(new Font("SansSerif", Font.BOLD, 22));
                    FontMetrics fm = g2.getFontMetrics();
                    g2.drawString(initials, x + (d - fm.stringWidth(initials)) / 2,
                            y + (d + fm.getAscent()) / 2 - 3);
                }
                g2.setClip(oldClip);

                g2.setColor(new Color(255, 255, 255, 45));
                g2.setStroke(new BasicStroke(1.5f));
                g2.draw(circle);
                g2.dispose();
            }
        }
    }


    // =========================================================
    // HISTORY PAGE (dashboard style)
    // INHERITANCE: HistoryPage extends AppPage.
    // ENCAPSULATION: table model, status maps and filters are all private.
    // =========================================================
    static class HistoryPage extends AppPage {

        private static final Color ORANGE = new Color(255, 170, 0);
        private static final Color BOX_BORDER = new Color(46, 53, 62);
        private static final Color ROW_A = new Color(17, 21, 27);
        private static final Color ROW_B = new Color(21, 26, 33);
        private static final Color ROW_SELECTED = new Color(58, 24, 28);

        private static final String[] TYPES = {"Cardiac", "Trauma", "General", "Critical Patient", "Accident"};
        private static final String[] TYPE_SHORT = {"Cardiac", "Trauma", "General", "Critical Pt.", "Accident"};
        private static final Color[] TYPE_COLORS = {
                new Color(245, 45, 45), new Color(255, 120, 95), new Color(185, 30, 42),
                new Color(255, 170, 150), new Color(120, 22, 32)};
        private static final String[] MONTHS = {"Jan", "Feb", "Mar", "Apr", "May", "Jun",
                "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};

        // [CHANGED] The table now also shows Caller, Patient and Hospital
        private final String[] columns = {"TIMESTAMP", "DISPATCH ID", "CALLER", "PATIENT", "HOSPITAL",
                "CALL TYPE", "PRIORITY", "STATUS", "AMBULANCE", "ACTION"};
        private static final int COL_ID = 1, COL_PRIORITY = 6, COL_STATUS = 7, COL_ACTION = 9;

        // INHERITANCE + POLYMORPHISM: anonymous subclass of DefaultTableModel
        // that overrides isCellEditable() so table cells are read-only.
        private final DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        private final JTable table = new JTable(model);

        // status is kept here so EmergencyRequest does not need to change
        private final Map<String, String> statusMap = new HashMap<String, String>();
        private final Map<String, Date> doneAt = new HashMap<String, Date>();
        private final List<EmergencyRequest> shown = new ArrayList<EmergencyRequest>();

        private final JComboBox<String> dateFilter = new JComboBox<String>(
                new String[]{"All Time", "Today", "Last 7 Days", "Last 30 Days"});
        private final JComboBox<String> typeFilter = new JComboBox<String>(
                new String[]{"All Types", "Cardiac", "Trauma", "General", "Critical Patient", "Accident"});
        private final JComboBox<String> priorityFilter = new JComboBox<String>(
                new String[]{"All Priorities", "Critical", "High", "Normal"});
        private final JComboBox<String> statusFilter = new JComboBox<String>(
                new String[]{"All Status", "Pending", "In-Route", "Complete", "Canceled"});

        // Extra filter set by clicking a dot on the trend chart (-1 = all months).
        private int monthFilter = -1;
        // True while filters are changed from code, so refreshData() runs only once.
        private boolean adjusting = false;

        private final JLabel totalValue = new JLabel("0");
        private final JLabel criticalValue = new JLabel("0");
        private final JLabel dwellValue = new JLabel("--");

        private final DonutChart donut = new DonutChart();
        private final TrendChart trend = new TrendChart();
        private final JLabel trendTitle = new JLabel("MONTHLY CALL VOLUME TREND");

        private final RoundBox reportBox = new RoundBox(false);
        private final JLabel reportTitle = new JLabel("PATIENT CARE REPORT");
        private final JLabel reportStatus = new JLabel(" ");
        private final JLabel reportBody = new JLabel();

        HistoryPage(Main app) {
            super(app);

            JPanel outer = new JPanel(new BorderLayout(20, 0));
            outer.setOpaque(false);
            outer.setBorder(new EmptyBorder(22, 32, 24, 32));
            outer.add(buildLeft(), BorderLayout.WEST);
            outer.add(buildRight(), BorderLayout.CENTER);
            add(outer, BorderLayout.CENTER);

            // FILE HANDLING: read the saved history file so old dispatches are visible
            // again after the program is restarted.
            List<EmergencyRequest> saved = FileManager.loadAll(statusMap);

// Dispatches from a previous session can no longer be in progress, because the
// program was closed. Anything not already Complete / Canceled is marked Complete.
            boolean changed = false;
            for (EmergencyRequest r : saved) {
                String st = statusMap.get(r.getId());
                if (st == null || st.equals("In-Route") || st.equals("Pending")) {
                    statusMap.put(r.getId(), "Complete");
                    changed = true;
                }
            }

            app.requests.addAll(saved);
            for (EmergencyRequest r : saved) app.bumpCounterFrom(r.getId());
            app.sessionStart = app.requests.size();

// Store the fixed statuses and the repaired names back into the file.
            if (changed || !saved.isEmpty()) FileManager.saveAll(saved, statusMap);

            refreshData();
        }

        // ---------------------------------------------------------
        // LEFT COLUMN: overview cards + donut
        // ---------------------------------------------------------
        private JPanel buildLeft() {
            JPanel left = new JPanel();
            left.setOpaque(false);
            left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
            left.setPreferredSize(new Dimension(290, 100));

            JLabel head = heading("HISTORY OVERVIEW");
            head.setAlignmentX(Component.LEFT_ALIGNMENT);
            left.add(head);
            left.add(Box.createVerticalStrut(12));
            left.add(statCard("TOTAL HISTORICAL DISPATCHES", totalValue, "▣", () -> setFilters(0, 0, 0, 0, -1)));
            left.add(Box.createVerticalStrut(10));
            left.add(statCard("CRITICAL INCIDENTS", criticalValue, "!", () -> setFilters(0, 0, 1, 0, -1)));
            left.add(Box.createVerticalStrut(10));
            left.add(statCard("AVERAGE DWELL TIME", dwellValue, "◔", () -> setFilters(0, 0, 0, 3, -1)));
            left.add(Box.createVerticalStrut(10));

            RoundBox donutBox = new RoundBox(true).enableHover();
            donutBox.setLayout(new BorderLayout(0, 6));
            donutBox.setBorder(new EmptyBorder(14, 16, 12, 16));
            donutBox.setAlignmentX(Component.LEFT_ALIGNMENT);
            donutBox.add(boxTitle("DISPATCH TYPE BREAKDOWN"), BorderLayout.NORTH);
            donutBox.add(donut, BorderLayout.CENTER);
            // Clicking a slice (or its legend row) filters the table by that call type.
            // Clicking the same slice again clears the filter.
            donut.setOnPick(index -> {
                String name = TYPES[index];
                boolean same = typeFilter.getSelectedItem().toString().equals(name);
                setFilters(0, same ? 0 : index + 1, 0, 0, -1);
            });
            left.add(donutBox);
            return left;
        }

        // Each overview card is clickable: 'onClick' sets the matching filters.
        private RoundBox statCard(String label, JLabel value, String icon, Runnable onClick) {
            RoundBox card = new RoundBox(true).enableHover();
            card.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) { onClick.run(); }
            });
            card.setLayout(new BorderLayout(14, 0));
            card.setBorder(new EmptyBorder(12, 16, 12, 16));
            card.setAlignmentX(Component.LEFT_ALIGNMENT);
            card.setPreferredSize(new Dimension(100, 84));
            card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 84));

            // INHERITANCE + POLYMORPHISM: anonymous subclass of JLabel that
            // overrides paintComponent() to draw a round icon background.
            JLabel ic = new JLabel(icon, SwingConstants.CENTER) {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(new Color(92, 26, 30));
                    g2.fillOval(0, 0, getWidth() - 1, getHeight() - 1);
                    g2.setColor(new Color(245, 45, 45, 120));
                    g2.drawOval(0, 0, getWidth() - 1, getHeight() - 1);
                    g2.dispose();
                    super.paintComponent(g);
                }
            };
            ic.setForeground(RED);
            ic.setFont(new Font("Dialog", Font.BOLD, 18));
            ic.setPreferredSize(new Dimension(44, 44));

            JPanel iconWrap = new JPanel(new GridBagLayout());
            iconWrap.setOpaque(false);
            iconWrap.add(ic);

            JLabel l = new JLabel(label);
            l.setForeground(MUTED);
            l.setFont(new Font("SansSerif", Font.BOLD, 10));

            value.setForeground(TEXT);
            value.setFont(new Font("SansSerif", Font.BOLD, 26));

            JPanel text = new JPanel();
            text.setOpaque(false);
            text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
            text.add(Box.createVerticalGlue());
            text.add(l);
            text.add(value);
            text.add(Box.createVerticalGlue());

            card.add(iconWrap, BorderLayout.WEST);
            card.add(text, BorderLayout.CENTER);
            return card;
        }

        // ---------------------------------------------------------
        // RIGHT COLUMN: filters, table, report, trend
        // ---------------------------------------------------------
        private JPanel buildRight() {
            JPanel right = new JPanel(new BorderLayout(0, 12));
            right.setOpaque(false);

            JPanel filters = new JPanel(new GridLayout(1, 5, 10, 0));
            filters.setOpaque(false);
            for (JComboBox<String> box : java.util.Arrays.asList(dateFilter, typeFilter, priorityFilter, statusFilter)) {
                styleCombo(box);
                box.addActionListener(e -> { if (!adjusting) refreshData(); });
                filters.add(box);
            }
            JButton refresh = new RoundedButton("REFRESH", false);
            refresh.addActionListener(e -> {
                table.clearSelection();
                setFilters(0, 0, 0, 0, -1);
            });
            filters.add(refresh);

            JPanel top = new JPanel(new BorderLayout(0, 10));
            top.setOpaque(false);
            top.add(heading("EMERGENCY DISPATCH HISTORY"), BorderLayout.NORTH);
            top.add(filters, BorderLayout.SOUTH);

            // table
            styleTable();
            JScrollPane scroll = new JScrollPane(table);
            scroll.setBorder(null);
            scroll.getViewport().setBackground(ROW_A);
            scroll.setBackground(ROW_A);

            RoundBox tableBox = new RoundBox(false);
            tableBox.setLayout(new BorderLayout());
            tableBox.setBorder(new EmptyBorder(6, 6, 6, 6));
            tableBox.add(scroll, BorderLayout.CENTER);

            // care report (shows the selected row)
            reportBox.setLayout(new BorderLayout(0, 6));
            reportBox.setBorder(new EmptyBorder(12, 18, 12, 18));
            reportBox.setPreferredSize(new Dimension(100, 128));

            reportTitle.setForeground(TEXT);
            reportTitle.setFont(new Font("SansSerif", Font.BOLD, 12));
            reportStatus.setFont(new Font("SansSerif", Font.BOLD, 11));
            JPanel reportHead = new JPanel(new BorderLayout());
            reportHead.setOpaque(false);
            reportHead.add(reportTitle, BorderLayout.WEST);
            reportHead.add(reportStatus, BorderLayout.EAST);

            reportBody.setForeground(TEXT);
            reportBody.setFont(new Font("SansSerif", Font.PLAIN, 12));
            reportBody.setVerticalAlignment(SwingConstants.TOP);

            reportBox.add(reportHead, BorderLayout.NORTH);
            reportBox.add(reportBody, BorderLayout.CENTER);

            // trend chart
            RoundBox trendBox = new RoundBox(true);
            trendBox.setLayout(new BorderLayout(0, 6));
            trendBox.setBorder(new EmptyBorder(12, 16, 10, 16));
            trendTitle.setForeground(TEXT);
            trendTitle.setFont(new Font("SansSerif", Font.BOLD, 13));
            trendBox.add(trendTitle, BorderLayout.NORTH);
            trendBox.add(trend, BorderLayout.CENTER);

            // LAYOUT FIX: the table, report and chart are stacked in a column that scrolls
            // vertically. Each block has its own fixed height, so the table can never be
            // squeezed to nothing on a small window; the page scrolls instead.
            tableBox.setPreferredSize(new Dimension(100, 300));
            tableBox.setMinimumSize(new Dimension(100, 300));
            tableBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 300));
            reportBox.setPreferredSize(new Dimension(100, 140));
            reportBox.setMinimumSize(new Dimension(100, 140));
            reportBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 140));
            trendBox.setPreferredSize(new Dimension(100, 260));
            trendBox.setMinimumSize(new Dimension(100, 260));
            trendBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 260));

            ScrollColumn column = new ScrollColumn();
            column.setOpaque(false);
            column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
            tableBox.setAlignmentX(Component.LEFT_ALIGNMENT);
            reportBox.setAlignmentX(Component.LEFT_ALIGNMENT);
            trendBox.setAlignmentX(Component.LEFT_ALIGNMENT);
            column.add(tableBox);
            column.add(Box.createVerticalStrut(12));
            column.add(reportBox);
            column.add(Box.createVerticalStrut(12));
            column.add(trendBox);

            JScrollPane page = new JScrollPane(column);
            page.setBorder(null);
            page.setOpaque(false);
            page.getViewport().setOpaque(false);
            page.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            page.getVerticalScrollBar().setUnitIncrement(18);

            right.add(top, BorderLayout.NORTH);
            right.add(page, BorderLayout.CENTER);

            // Clicking a dot on the trend chart filters the table by that month.
            // Clicking the same month again clears it.
            trend.setOnPick(month -> {
                monthFilter = (monthFilter == month) ? -1 : month;
                refreshData();
            });

            table.getSelectionModel().addListSelectionListener(e -> {
                if (!e.getValueIsAdjusting()) showReport();
            });
            return right;
        }

        // A panel that always matches the width of the scroll pane (only height scrolls).
        // INHERITANCE: extends JPanel and implements the Scrollable interface.
        private static class ScrollColumn extends JPanel implements Scrollable {
            @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
            @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 18; }
            @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return 120; }
            @Override public boolean getScrollableTracksViewportWidth() { return true; }
            @Override public boolean getScrollableTracksViewportHeight() { return false; }
        }

        // Sets all four combo boxes + the month filter from code, then refreshes once.
        private void setFilters(int date, int type, int priority, int status, int month) {
            adjusting = true;
            dateFilter.setSelectedIndex(date);
            typeFilter.setSelectedIndex(type);
            priorityFilter.setSelectedIndex(priority);
            statusFilter.setSelectedIndex(status);
            monthFilter = month;
            adjusting = false;
            refreshData();
        }

        private JLabel heading(String text) {
            JLabel l = new JLabel(text);
            l.setForeground(TEXT);
            l.setFont(new Font("SansSerif", Font.BOLD, 20));
            return l;
        }

        private JLabel boxTitle(String text) {
            JLabel l = new JLabel(text);
            l.setForeground(TEXT);
            l.setFont(new Font("SansSerif", Font.BOLD, 13));
            return l;
        }

        private void styleCombo(JComboBox<String> box) {
            box.setBackground(new Color(21, 26, 33));
            box.setForeground(TEXT);
            box.setFont(new Font("SansSerif", Font.PLAIN, 12));
            box.setFocusable(false);
            box.setPreferredSize(new Dimension(100, 36));
        }

        private void styleTable() {
            table.setBackground(ROW_A);
            table.setForeground(TEXT);
            table.setShowGrid(false);
            table.setIntercellSpacing(new Dimension(0, 0));
            table.setRowHeight(38);
            table.setFillsViewportHeight(true);
            table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            table.setFocusable(false);

            CellRenderer renderer = new CellRenderer();
            for (int i = 0; i < columns.length; i++) {
                table.getColumnModel().getColumn(i).setCellRenderer(renderer);
            }
            int[] widths = {120, 85, 85, 85, 120, 80, 65, 80, 75, 50};
            for (int i = 0; i < widths.length; i++) {
                table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
            }

            javax.swing.table.JTableHeader header = table.getTableHeader();
            header.setReorderingAllowed(false);
            header.setPreferredSize(new Dimension(100, 34));
            // POLYMORPHISM: anonymous subclass overriding the header renderer.
            header.setDefaultRenderer(new DefaultTableCellRenderer() {
                @Override
                public Component getTableCellRendererComponent(JTable t, Object v, boolean sel,
                                                               boolean focus, int row, int col) {
                    JLabel l = (JLabel) super.getTableCellRendererComponent(t, v, false, false, row, col);
                    l.setOpaque(true);
                    l.setBackground(new Color(14, 18, 22));
                    l.setForeground(MUTED);
                    l.setFont(new Font("SansSerif", Font.BOLD, 11));
                    l.setBorder(new EmptyBorder(0, 10, 0, 10));
                    l.setHorizontalAlignment(col == COL_ACTION ? SwingConstants.CENTER : SwingConstants.LEFT);
                    return l;
                }
            });

            table.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    int row = table.rowAtPoint(e.getPoint());
                    int col = table.columnAtPoint(e.getPoint());
                    if (row < 0 || row >= shown.size()) return;
                    if (col == COL_ACTION) {
                        table.setRowSelectionInterval(row, row);
                        showActionMenu(e.getX(), e.getY(), shown.get(row));
                    }
                }
            });
            table.addMouseMotionListener(new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    boolean onAction = table.columnAtPoint(e.getPoint()) == COL_ACTION
                            && table.rowAtPoint(e.getPoint()) >= 0;
                    table.setCursor(Cursor.getPredefinedCursor(onAction ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
                }
            });
        }

        // INHERITANCE: CellRenderer extends DefaultTableCellRenderer.
        // POLYMORPHISM: it overrides getTableCellRendererComponent() so each
        // column (status, priority, action) is drawn in its own style.
        private class CellRenderer extends DefaultTableCellRenderer {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object v, boolean sel,
                                                           boolean focus, int row, int col) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(t, v, false, false, row, col);
                String text = v == null ? "" : v.toString();

                l.setOpaque(true);
                l.setBackground(sel ? ROW_SELECTED : (row % 2 == 0 ? ROW_A : ROW_B));
                l.setForeground(TEXT);
                l.setFont(new Font("SansSerif", col == COL_ID ? Font.BOLD : Font.PLAIN, 12));
                l.setBorder(new EmptyBorder(0, 10, 0, 10));
                l.setHorizontalAlignment(SwingConstants.LEFT);

                if (col == COL_STATUS) {
                    l.setText("<html><span style='color:" + statusHex(text) + "'>●</span>&nbsp; " + text + "</html>");
                } else if (col == COL_PRIORITY) {
                    if (text.equals("Critical")) l.setForeground(RED);
                    else if (text.equals("High")) l.setForeground(ORANGE);
                    else l.setForeground(MUTED);
                } else if (col == COL_ACTION) {
                    l.setText("•••");
                    l.setForeground(sel ? TEXT : MUTED);
                    l.setHorizontalAlignment(SwingConstants.CENTER);
                }
                return l;
            }
        }

        // ---------------------------------------------------------
        // STATUS HANDLING
        // ---------------------------------------------------------
        private String statusOf(EmergencyRequest r) {
            String s = statusMap.get(r.getId());
            if (s != null) return s;
            return "WAITING".equals(r.getAmbulanceId()) ? "Pending" : "In-Route";
        }

        private static String statusHex(String status) {
            switch (status) {
                case "Complete": return "#46D27D";
                case "Canceled": return "#F52D2D";
                case "In-Route": return "#FFAA00";
                default: return "#A0A5AA";
            }
        }

        private static Color statusColor(String status) {
            switch (status) {
                case "Complete": return GREEN;
                case "Canceled": return RED;
                case "In-Route": return ORANGE;
                default: return MUTED;
            }
        }

        private void showActionMenu(int x, int y, final EmergencyRequest r) {
            String status = statusOf(r);
            boolean open = status.equals("In-Route") || status.equals("Pending");

            JPopupMenu menu = new JPopupMenu();
            JMenuItem complete = new JMenuItem("Mark as Complete");
            JMenuItem cancel = new JMenuItem("Cancel Dispatch");
            complete.setEnabled(open);
            cancel.setEnabled(open);
            complete.addActionListener(e -> closeRequest(r, "Complete"));
            cancel.addActionListener(e -> closeRequest(r, "Canceled"));
            menu.add(complete);
            menu.add(cancel);
            menu.show(table, x, y);
        }

        private void closeRequest(EmergencyRequest r, String status) {
            statusMap.put(r.getId(), status);
            if (status.equals("Complete")) doneAt.put(r.getId(), new Date());

            // free the ambulance so it can be dispatched again
            for (AmbulanceUnit a : app.ambulances) {
                if (a.getId().equals(r.getAmbulanceId())) a.setStatus("Available");
            }
            // FILE HANDLING: rewrite the history file so the new status is stored on disk.
            FileManager.saveAll(app.requests, statusMap);
            refreshData();
        }

        // ---------------------------------------------------------
        // CARE REPORT PANEL
        // ---------------------------------------------------------
        private static String esc(String s) {
            if (s == null || s.trim().isEmpty()) return "-";
            return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }

        private static String cut(String s, int max) {
            if (s == null) return "";
            return s.length() > max ? s.substring(0, max - 3) + "..." : s;
        }

        private void showReport() {
            int row = table.getSelectedRow();
            if (row < 0 || row >= shown.size()) {
                reportTitle.setText("PATIENT CARE REPORT");
                reportStatus.setText(" ");
                reportBody.setText("<html><span style='color:#A0A5AA'>Select a dispatch in the table to see "
                        + "its patient care report.</span></html>");
                reportBox.setBorderColor(BOX_BORDER);
                return;
            }

            EmergencyRequest r = shown.get(row);
            String status = statusOf(r);
            String outcome;
            switch (status) {
                case "Complete":
                    outcome = "Patient delivered to " + esc(r.getHospitalName()) + ". Dispatch closed.";
                    break;
                case "Canceled":
                    outcome = "Dispatch was canceled before completion.";
                    break;
                case "Pending":
                    outcome = "Waiting for a free ambulance to be assigned.";
                    break;
                default:
                    outcome = "Ambulance en route, patient care in progress.";
            }

            String lbl = "<span style='color:#A0A5AA'>";
            String sep = " &nbsp;•&nbsp; ";
            String html = "<html>"
                    + lbl + "Caller:</span> <b>" + esc(r.getCallerName()) + "</b>" + sep
                    + lbl + "Patient:</span> <b>" + esc(r.getPatientName()) + "</b>" + sep
                    + lbl + "Phone:</span> " + esc(r.getPhone()) + sep
                    + lbl + "Type:</span> " + esc(r.getEmergencyType()) + "<br>"
                    + lbl + "Location:</span> " + esc(cut(r.getLocation(), 60)) + sep
                    + lbl + "Ambulance:</span> " + esc(r.getAmbulanceId()) + "<br>"
                    + lbl + "Hospital:</span> " + esc(r.getHospitalName())
                    + String.format(" (%.1f km)", r.getHospitalDistance()) + "<br>"
                    + lbl + "Notes:</span> " + esc(cut(r.getNotes(), 90)) + "<br>"
                    + lbl + "Outcome:</span> <b>" + outcome + "</b>"
                    + "</html>";

            reportTitle.setText("PATIENT CARE REPORT (PCR) for " + r.getId());
            reportStatus.setText(status.toUpperCase());
            reportStatus.setForeground(statusColor(status));
            reportBody.setText(html);
            reportBox.setBorderColor(new Color(245, 45, 45, 170));
        }

        // ---------------------------------------------------------
        // DATA
        // ---------------------------------------------------------
        private String selectedId() {
            int row = table.getSelectedRow();
            if (row < 0 || row >= shown.size()) return null;
            return shown.get(row).getId();
        }

        private boolean matchesDate(EmergencyRequest r, String filter) {
            if (filter.equals("All Time")) return true;
            long age = System.currentTimeMillis() - r.getDate().getTime();
            long day = 24L * 60 * 60 * 1000;
            if (filter.equals("Today")) {
                SimpleDateFormat f = new SimpleDateFormat("yyyyMMdd");
                return f.format(r.getDate()).equals(f.format(new Date()));
            }
            if (filter.equals("Last 7 Days")) return age <= 7 * day;
            return age <= 30 * day;
        }

        private static String formatDuration(long millis) {
            long secs = Math.max(0, millis / 1000);
            return (secs / 60) + "m " + String.format("%02d", secs % 60) + "s";
        }

        // POLYMORPHISM: HistoryPage's own refreshData(). It recalculates the
        // statistics, applies the filters, refills the table and redraws the
        // charts. (The history shown here comes from the in-memory list; the
        // same requests are also saved to history.txt by FileManager.)
        @Override
        void refreshData() {
            String keep = selectedId();
            List<EmergencyRequest> all = app.requests;

            // ----- overview cards -----
            int critical = 0;
            long dwellTotal = 0;
            int dwellCount = 0;
            int[] typeCounts = new int[TYPES.length];
            int[] monthly = new int[12];
            Calendar cal = Calendar.getInstance();
            int thisYear = cal.get(Calendar.YEAR);
            int thisMonth = cal.get(Calendar.MONTH);

            for (EmergencyRequest r : all) {
                if (r.getPriority().equals("Critical")) critical++;

                // Charts count only NON-canceled dispatches: a new dispatch makes the graph
                // go up, and canceling one makes it go down again.
                if (statusOf(r).equals("Canceled")) continue;

                Date done = doneAt.get(r.getId());
                if (done != null) {
                    dwellTotal += done.getTime() - r.getDate().getTime();
                    dwellCount++;
                }
                for (int i = 0; i < TYPES.length; i++) {
                    if (TYPES[i].equals(r.getEmergencyType())) typeCounts[i]++;
                }
                cal.setTime(r.getDate());
                if (cal.get(Calendar.YEAR) == thisYear) monthly[cal.get(Calendar.MONTH)]++;
            }

            totalValue.setText(String.format("%,d", all.size()));
            criticalValue.setText(String.valueOf(critical));
            dwellValue.setText(dwellCount == 0 ? "--" : formatDuration(dwellTotal / dwellCount));

            // ----- table (newest first) -----
            String date = dateFilter.getSelectedItem().toString();
            String type = typeFilter.getSelectedItem().toString();
            String priority = priorityFilter.getSelectedItem().toString();
            String status = statusFilter.getSelectedItem().toString();
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm");

            model.setRowCount(0);
            shown.clear();
            for (int i = all.size() - 1; i >= 0; i--) {
                EmergencyRequest r = all.get(i);
                if (!matchesDate(r, date)) continue;
                if (!type.equals("All Types") && !r.getEmergencyType().equals(type)) continue;
                if (!priority.equals("All Priorities") && !r.getPriority().equals(priority)) continue;
                if (!status.equals("All Status") && !statusOf(r).equals(status)) continue;
                if (monthFilter >= 0) {
                    Calendar c = Calendar.getInstance();
                    c.setTime(r.getDate());
                    if (c.get(Calendar.YEAR) != thisYear || c.get(Calendar.MONTH) != monthFilter) continue;
                }

                shown.add(r);
                model.addRow(new Object[]{fmt.format(r.getDate()), r.getId(),
                        r.getCallerName(), r.getPatientName(), r.getHospitalName(),
                        r.getEmergencyType(), r.getPriority(), statusOf(r), r.getAmbulanceId(), ""});
            }

            // keep the same row selected after a refresh
            if (keep != null) {
                for (int i = 0; i < shown.size(); i++) {
                    if (shown.get(i).getId().equals(keep)) {
                        table.setRowSelectionInterval(i, i);
                        break;
                    }
                }
            }

            // ----- charts -----
            donut.setData(typeCounts);
            donut.setSelected(java.util.Arrays.asList(TYPES).indexOf(type));
            trend.setData(monthly, thisMonth);
            trend.setSelectedMonth(monthFilter);
            trendTitle.setText("MONTHLY CALL VOLUME TREND  •  " + thisYear
                    + (monthFilter >= 0 ? "  •  FILTER: " + MONTHS[monthFilter] + " (click again to clear)"
                    : "  •  click a dot to filter"));

            showReport();
        }

        // =====================================================
        // ROUNDED DARK BOX (optionally tinted red like the mockup)
        // INHERITANCE: extends JPanel.
        // ENCAPSULATION: private fields, border changed via setBorderColor().
        // =====================================================
        static class RoundBox extends JPanel {

            private final boolean tinted;
            private Color border = BOX_BORDER;

            RoundBox(boolean tinted) {
                this.tinted = tinted;
                setOpaque(false);
            }

            // ---- hover lift (used by the History overview cards) ----
            private static final int LIFT = 5;
            private boolean hoverable = false;
            private float hp = 0f;
            private javax.swing.Timer hoverTimer;

            RoundBox enableHover() {
                hoverable = true;
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                hoverTimer = new javax.swing.Timer(15, e -> {
                    float target = getMousePosition(true) != null ? 1f : 0f;
                    hp += (target - hp) * 0.25f;
                    if (Math.abs(target - hp) < 0.02f) {
                        hp = target;
                        ((javax.swing.Timer) e.getSource()).stop();
                    }
                    repaint();
                });
                addMouseListener(new MouseAdapter() {
                    @Override public void mouseEntered(MouseEvent e) { hoverTimer.start(); }
                    @Override public void mouseExited(MouseEvent e) { hoverTimer.start(); }
                });
                return this;
            }

            @Override
            protected void paintChildren(Graphics g) {
                if (!hoverable) { super.paintChildren(g); return; }
                Graphics2D g2 = (Graphics2D) g.create();
                g2.translate(0, -LIFT * hp);
                super.paintChildren(g2);
                g2.dispose();
            }

            void setBorderColor(Color c) {
                border = c;
                repaint();
            }

            // POLYMORPHISM: overrides paintComponent().
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth(), h = getHeight();
                int top = hoverable ? Math.round(LIFT * (1 - hp)) : 0;

                if (tinted) {
                    g2.setPaint(new GradientPaint(0, 0, new Color(24, 20, 25), w, h, new Color(88 + (int) (40 * hp), 20, 22)));
                } else {
                    g2.setColor(new Color(17, 21, 27));
                }
                g2.fillRoundRect(0, top, w - 1, h - 1 - top, 16, 16);

                g2.setColor(hoverable && hp > 0f
                        ? new Color(245, 45, 45, (int) (90 + 165 * hp)) : border);
                g2.setStroke(new BasicStroke(1.2f + 1.3f * hp));
                g2.drawRoundRect(0, top, w - 1, h - 1 - top, 16, 16);
                if (hoverable && hp > 0f) {
                    int lw = (int) ((w - 30) * hp);
                    g2.setColor(RED);
                    g2.fillRoundRect((w - lw) / 2, h - 5, lw, 3, 3, 3);
                }
                g2.dispose();
                super.paintComponent(g);
            }
        }

        // =====================================================
        // DONUT CHART (clickable)
        // INHERITANCE: extends JComponent. ENCAPSULATION: private data,
        // updated only through setData() / setSelected().
        // Click a slice or a legend row -> onPick(index) is called.
        // =====================================================
        static class DonutChart extends JComponent {

            private int[] counts = new int[TYPES.length];
            private int selected = -1;          // slice drawn thicker (current filter)
            private int hover = -1;             // slice under the mouse
            private java.util.function.IntConsumer onPick;

            DonutChart() {
                MouseAdapter ma = new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        int i = indexAt(e.getPoint());
                        if (i >= 0 && onPick != null) onPick.accept(i);
                    }
                    @Override
                    public void mouseMoved(MouseEvent e) {
                        int i = indexAt(e.getPoint());
                        if (i != hover) { hover = i; repaint(); }
                        setCursor(Cursor.getPredefinedCursor(i >= 0 ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
                    }
                    @Override
                    public void mouseExited(MouseEvent e) { hover = -1; repaint(); }
                };
                addMouseListener(ma);
                addMouseMotionListener(ma);
            }

            void setOnPick(java.util.function.IntConsumer c) { onPick = c; }

            void setData(int[] c) {
                counts = c;
                repaint();
            }

            void setSelected(int index) {
                selected = index;
                repaint();
            }

            // Geometry shared by painting and mouse hit-testing.
            private int diameter() { return Math.min(getHeight() - 6, (int) (getWidth() * 0.46)); }

            // Returns the slice or legend row under the point, or -1.
            private int indexAt(Point p) {
                int w = getWidth(), h = getHeight();
                int d = diameter();
                if (d < 40) return -1;
                int x = 2, y = (h - d) / 2;

                // legend rows
                int lx = x + d + 16;
                int rowH = Math.min(20, (h - 4) / TYPES.length);
                int ly = (h - rowH * TYPES.length) / 2;
                if (p.x >= lx && p.x <= w && p.y >= ly && p.y < ly + rowH * TYPES.length) {
                    return (p.y - ly) / rowH;
                }

                // ring slices
                int total = 0;
                for (int c : counts) total += c;
                if (total == 0) return -1;
                double cx = x + d / 2.0, cy = y + d / 2.0;
                double dx = p.x - cx, dy = p.y - cy;
                double dist = Math.sqrt(dx * dx + dy * dy);
                float thick = d * 0.20f;
                if (dist > d / 2.0 + 4 || dist < d / 2.0 - thick - 4) return -1;

                // angle measured clockwise from 12 o'clock (same as the painting order)
                double ang = Math.toDegrees(Math.atan2(-dy, dx));
                double fromTop = (90 - ang + 360) % 360;
                double acc = 0;
                for (int i = 0; i < counts.length; i++) {
                    acc += 360.0 * counts[i] / total;
                    if (counts[i] > 0 && fromTop <= acc) return i;
                }
                return -1;
            }

            // POLYMORPHISM: overrides paintComponent().
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                int w = getWidth(), h = getHeight();
                int d = diameter();
                if (d < 40) {
                    g2.dispose();
                    return;
                }

                int total = 0;
                for (int c : counts) total += c;

                int x = 2, y = (h - d) / 2;
                float thick = d * 0.20f;
                double inset = thick / 2.0;

                if (total == 0) {
                    g2.setStroke(new BasicStroke(thick));
                    g2.setColor(new Color(50, 56, 64));
                    g2.draw(new Ellipse2D.Double(x + inset, y + inset, d - thick, d - thick));
                } else {
                    double start = 90;
                    for (int i = 0; i < counts.length; i++) {
                        if (counts[i] == 0) continue;
                        double extent = -360.0 * counts[i] / total;
                        // the selected / hovered slice is drawn a little thicker
                        float t = (i == selected || i == hover) ? thick * 1.18f : thick;
                        g2.setStroke(new BasicStroke(t, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
                        g2.setColor(TYPE_COLORS[i]);
                        g2.draw(new Arc2D.Double(x + inset, y + inset, d - thick, d - thick,
                                start, extent, Arc2D.OPEN));
                        start += extent;
                    }
                }

                // total in the middle
                String t = String.valueOf(total);
                g2.setColor(TEXT);
                g2.setFont(new Font("SansSerif", Font.BOLD, Math.max(12, d / 4)));
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(t, x + (d - fm.stringWidth(t)) / 2, y + d / 2 + fm.getAscent() / 2 - 3);

                // legend
                int lx = x + d + 16;
                int rowH = Math.min(20, (h - 4) / TYPES.length);
                int ly = (h - rowH * TYPES.length) / 2;
                for (int i = 0; i < TYPES.length; i++) {
                    int cy = ly + i * rowH + rowH / 2;
                    boolean on = (i == selected || i == hover);
                    g2.setFont(new Font("SansSerif", on ? Font.BOLD : Font.PLAIN, 11));
                    g2.setColor(TYPE_COLORS[i]);
                    g2.fillOval(lx, cy - 4, 9, 9);
                    g2.setColor(on ? Color.WHITE : new Color(215, 220, 225));
                    g2.drawString(TYPE_SHORT[i] + "  " + counts[i], lx + 15, cy + 4);
                }
                g2.dispose();
            }
        }

        // =====================================================
        // MONTHLY TREND CHART (smooth red area line, animated + clickable)
        // INHERITANCE: extends JComponent. ENCAPSULATION: private data,
        // updated only through setData().
        // The drawn values glide to the new values with a Swing Timer, so the
        // graph visibly goes UP when a dispatch is added and DOWN when one is canceled.
        // =====================================================
        static class TrendChart extends JComponent {

            private int[] target = new int[12];          // real values
            private double[] shown = new double[12];     // values currently drawn (animated)
            private int currentMonth = 0;
            private int selectedMonth = -1;
            private int hoverMonth = -1;
            private java.util.function.IntConsumer onPick;
            private final javax.swing.Timer anim;

            // chart margins (shared by painting and hit-testing)
            private static final int LEFT = 30, RIGHT = 10, TOP = 8, BOTTOM = 20;

            TrendChart() {
                anim = new javax.swing.Timer(16, e -> {
                    boolean moving = false;
                    for (int i = 0; i < 12; i++) {
                        double diff = target[i] - shown[i];
                        if (Math.abs(diff) > 0.01) { shown[i] += diff * 0.18; moving = true; }
                        else shown[i] = target[i];
                    }
                    repaint();
                    if (!moving) ((javax.swing.Timer) e.getSource()).stop();
                });

                MouseAdapter ma = new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        int m = monthAt(e.getPoint());
                        if (m >= 0 && onPick != null) onPick.accept(m);
                    }
                    @Override
                    public void mouseMoved(MouseEvent e) {
                        int m = monthAt(e.getPoint());
                        if (m != hoverMonth) { hoverMonth = m; repaint(); }
                        setCursor(Cursor.getPredefinedCursor(m >= 0 ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
                    }
                    @Override
                    public void mouseExited(MouseEvent e) { hoverMonth = -1; repaint(); }
                };
                addMouseListener(ma);
                addMouseMotionListener(ma);
            }

            void setOnPick(java.util.function.IntConsumer c) { onPick = c; }

            void setSelectedMonth(int m) { selectedMonth = m; repaint(); }

            void setData(int[] d, int currentMonth) {
                this.target = d.clone();
                this.currentMonth = currentMonth;
                anim.start();            // animate from the old values to the new ones
            }

            // Which month column is closest to the mouse (only months up to the current one).
            private int monthAt(Point p) {
                int cw = getWidth() - LEFT - RIGHT;
                if (cw < 50) return -1;
                if (p.y < 0 || p.y > getHeight()) return -1;
                int best = -1;
                double bestDist = 22;                     // click tolerance in pixels
                for (int i = 0; i <= currentMonth; i++) {
                    double px = LEFT + i * cw / 11.0;
                    double dist = Math.abs(p.x - px);
                    if (dist < bestDist) { bestDist = dist; best = i; }
                }
                return best;
            }

            // POLYMORPHISM: overrides paintComponent().
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                int w = getWidth(), h = getHeight();
                int left = LEFT, right = RIGHT, top = TOP, bottom = BOTTOM;
                int cw = w - left - right, ch = h - top - bottom;
                if (cw < 50 || ch < 30) {
                    g2.dispose();
                    return;
                }

                // the y-axis grows / shrinks with the data (steps of 4)
                double max = 0;
                for (double v : shown) max = Math.max(max, v);
                int axisMax = Math.max(4, (((int) Math.ceil(max)) + 3) / 4 * 4);

                // grid + y labels
                g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
                g2.setStroke(new BasicStroke(1f));
                for (int i = 0; i <= 4; i++) {
                    int gy = top + ch - ch * i / 4;
                    g2.setColor(new Color(255, 255, 255, 22));
                    g2.drawLine(left, gy, left + cw, gy);
                    String lab = String.valueOf(axisMax * i / 4);
                    g2.setColor(MUTED);
                    g2.drawString(lab, left - 8 - g2.getFontMetrics().stringWidth(lab), gy + 4);
                }

                // month labels
                for (int i = 0; i < 12; i++) {
                    int mx = left + (int) Math.round(i * cw / 11.0);
                    g2.setFont(new Font("SansSerif", i == selectedMonth ? Font.BOLD : Font.PLAIN, 10));
                    g2.setColor(i == selectedMonth ? RED : (i == currentMonth ? TEXT : MUTED));
                    g2.drawString(MONTHS[i], mx - g2.getFontMetrics().stringWidth(MONTHS[i]) / 2, h - 5);
                }
                g2.setFont(new Font("SansSerif", Font.PLAIN, 10));

                // points (only up to the current month)
                int n = currentMonth + 1;
                double[] xs = new double[n], ys = new double[n];
                for (int i = 0; i < n; i++) {
                    xs[i] = left + i * cw / 11.0;
                    ys[i] = top + ch - (shown[i] / axisMax) * ch;
                }

                double bottomY = top + ch;
                if (n >= 2) {
                    Path2D.Double line = new Path2D.Double();
                    line.moveTo(xs[0], ys[0]);
                    addCurve(line, xs, ys, top, bottomY);

                    Path2D.Double area = new Path2D.Double();
                    area.moveTo(xs[0], bottomY);
                    area.lineTo(xs[0], ys[0]);
                    addCurve(area, xs, ys, top, bottomY);
                    area.lineTo(xs[n - 1], bottomY);
                    area.closePath();

                    g2.setPaint(new GradientPaint(0, top, new Color(245, 45, 45, 140),
                            0, (float) bottomY, new Color(245, 45, 45, 8)));
                    g2.fill(area);

                    g2.setColor(new Color(245, 45, 45, 60));
                    g2.setStroke(new BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.draw(line);
                    g2.setColor(new Color(255, 85, 85));
                    g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.draw(line);
                }

                // dots: current month white, selected / hovered month bigger with a ring
                for (int i = 0; i < n; i++) {
                    boolean cur = i == n - 1;
                    boolean active = i == selectedMonth || i == hoverMonth;
                    g2.setColor(cur ? Color.WHITE : new Color(255, 85, 85));
                    int r = active ? 6 : (cur ? 4 : 3);
                    g2.fillOval((int) xs[i] - r, (int) ys[i] - r, r * 2, r * 2);
                    if (active) {
                        g2.setColor(new Color(255, 255, 255, 190));
                        g2.setStroke(new BasicStroke(1.5f));
                        g2.drawOval((int) xs[i] - r - 3, (int) ys[i] - r - 3, (r + 3) * 2, (r + 3) * 2);
                    }
                }

                // tooltip bubble for the hovered month: "Oct: 2 calls"
                if (hoverMonth >= 0 && hoverMonth < n) {
                    String tip = MONTHS[hoverMonth] + ": " + target[hoverMonth]
                            + (target[hoverMonth] == 1 ? " call" : " calls");
                    g2.setFont(new Font("SansSerif", Font.BOLD, 11));
                    FontMetrics fm = g2.getFontMetrics();
                    int bw = fm.stringWidth(tip) + 14, bh = 22;
                    int bx = (int) Math.max(left, Math.min(xs[hoverMonth] - bw / 2.0, w - bw - 2));
                    int by = (int) Math.max(2, ys[hoverMonth] - bh - 12);
                    g2.setColor(new Color(20, 25, 30, 240));
                    g2.fillRoundRect(bx, by, bw, bh, 8, 8);
                    g2.setColor(RED);
                    g2.drawRoundRect(bx, by, bw, bh, 8, 8);
                    g2.setColor(Color.WHITE);
                    g2.drawString(tip, bx + 7, by + 15);
                }
                g2.dispose();
            }

            // Catmull-Rom spline converted to cubic curves
            private void addCurve(Path2D.Double p, double[] xs, double[] ys, double minY, double maxY) {
                int n = xs.length;
                for (int i = 0; i < n - 1; i++) {
                    int p0 = i > 0 ? i - 1 : i;
                    int p3 = i + 2 < n ? i + 2 : i + 1;
                    double c1x = xs[i] + (xs[i + 1] - xs[p0]) / 6.0;
                    double c1y = ys[i] + (ys[i + 1] - ys[p0]) / 6.0;
                    double c2x = xs[i + 1] - (xs[p3] - xs[i]) / 6.0;
                    double c2y = ys[i + 1] - (ys[p3] - ys[i]) / 6.0;
                    c1y = Math.max(minY, Math.min(maxY, c1y));
                    c2y = Math.max(minY, Math.min(maxY, c2y));
                    p.curveTo(c1x, c1y, c2x, c2y, xs[i + 1], ys[i + 1]);
                }
            }
        }
    }



    // =========================================================
    // EMERGENCY REQUEST
    // ENCAPSULATION: this is a data class. Every field is private and can
    // only be read through public getters or changed through public setters.
    // Fields that never change after creation (id, name, phone, ...) have
    // no setter at all, so they are effectively read-only.
    // =========================================================
    static class EmergencyRequest {

        private String id;
        private String callerName;     // [NEW] jisne call ki (caller)
        private String patientName;    // [NEW] jise ambulance chahiye (patient)
        private String phone;          // caller ka phone number
        private String location;
        private String emergencyType;
        private String priority;
        private String notes;
        private Date date;
        private String ambulanceId = "WAITING";
        private String hospitalName = "Not Assigned";
        private double hospitalDistance = 0;

        EmergencyRequest(String id, String callerName, String patientName, String phone, String location,
                         String emergencyType, String priority, String notes, Date date) {
            this.id = id;
            this.callerName = callerName;
            this.patientName = patientName;
            this.phone = phone;
            this.location = location;
            this.emergencyType = emergencyType;
            this.priority = priority;
            this.notes = notes;
            this.date = date;
        }

        // Getters (read access)
        public String getId() { return id; }
        public String getCallerName() { return callerName; }
        public String getPatientName() { return patientName; }
        // getName() now returns the patient name (older code still uses it)
        public String getName() { return patientName; }
        public String getPhone() { return phone; }
        public String getLocation() { return location; }
        public String getEmergencyType() { return emergencyType; }
        public String getPriority() { return priority; }
        public String getNotes() { return notes; }
        public Date getDate() { return date; }
        public String getAmbulanceId() { return ambulanceId; }
        public String getHospitalName() { return hospitalName; }
        public double getHospitalDistance() { return hospitalDistance; }

        // Setters (controlled write access, only for values assigned after creation)
        public void setAmbulanceId(String ambulanceId) { this.ambulanceId = ambulanceId; }
        public void setHospitalName(String hospitalName) { this.hospitalName = hospitalName; }
        public void setHospitalDistance(double d) { this.hospitalDistance = d; }
        public void setLocation(String location) { this.location = location; }
    }

    // =========================================================
    // AMBULANCE DATA
    // ENCAPSULATION: private fields with public getters and setters. The id
    // is read-only; only the status and location can change.
    // =========================================================
    static class AmbulanceUnit {

        private String id;
        private String status;
        private String currentLocation;

        AmbulanceUnit(String id, String status, String currentLocation) {
            this.id = id;
            this.status = status;
            this.currentLocation = currentLocation;
        }

        public String getId() { return id; }
        public String getStatus() { return status; }
        public String getCurrentLocation() { return currentLocation; }
        public void setStatus(String status) { this.status = status; }
        public void setCurrentLocation(String location) { this.currentLocation = location; }
    }

    // =========================================================
    // HOSPITAL DATA
    // ENCAPSULATION: private fields with public getters only, so hospital
    // data cannot be changed from outside after it is created (read-only).
    // =========================================================
    static class HospitalData {

        private String name;
        private double distance;
        private String specialties;
        private int totalBeds;
        private int availableBeds;
        private String phone;

        HospitalData(String name, double distance, String specialties,
                     int totalBeds, int availableBeds, String phone) {
            this.name = name;
            this.distance = distance;
            this.specialties = specialties;
            this.totalBeds = totalBeds;
            this.availableBeds = availableBeds;
            this.phone = phone;
        }

        public String getName() { return name; }
        public double getDistance() { return distance; }
        public String getSpecialties() { return specialties; }
        public int getTotalBeds() { return totalBeds; }
        public int getAvailableBeds() { return availableBeds; }
        public String getPhone() { return phone; }

        // [NEW] Reserve 1 bed (count -1). Never goes below 0.
        public void reserveBed() {
            if (availableBeds > 0) availableBeds--;
        }

        @Override
        public String toString() {
            return name + "  •  " + String.format("%.1f km", distance) + "  •  " + availableBeds + " beds";
        }
    }

    // =========================================================
    // FILE HANDLING
    // FileManager is a utility class: it keeps all reading and writing of
    // history.txt in one place (ENCAPSULATION). The file name is private, so
    // other classes cannot change where data is saved.
    // =========================================================
    static class FileManager {

        // FILE HANDLING: the history is stored as plain text in the user's home folder.
        private static final String FILE_NAME =
                System.getProperty("user.home") + File.separator + "MediRoute_history.txt";
        private static final String DATE_FORMAT = "dd-MM-yyyy HH:mm:ss";
        private static final String LINE = "====================================================";

        // Removes line breaks so one field can never fake extra lines in the file.
        private static String clean(String s) {
            return s == null ? "" : s.replaceAll("[\\r\\n]+", " ").trim();
        }

        // Writes ONE request block to an already open writer (shared by save + rewrite).
        private static void writeBlock(BufferedWriter writer, EmergencyRequest request, String status)
                throws IOException {
            String date = new SimpleDateFormat(DATE_FORMAT).format(request.getDate());
            writer.write(LINE);                                                        writer.newLine();
            writer.write("Dispatch ID   : " + request.getId());                        writer.newLine();
            writer.write("Caller        : " + clean(request.getCallerName()));         writer.newLine();
            writer.write("Patient       : " + clean(request.getPatientName()));        writer.newLine();
            writer.write("Phone         : " + clean(request.getPhone()));              writer.newLine();
            writer.write("Location      : " + clean(request.getLocation()));           writer.newLine();
            writer.write("Emergency     : " + request.getEmergencyType());             writer.newLine();
            writer.write("Priority      : " + request.getPriority());                  writer.newLine();
            writer.write("Ambulance     : " + request.getAmbulanceId());               writer.newLine();
            writer.write("Hospital      : " + request.getHospitalName());              writer.newLine();
            writer.write("Distance      : " + request.getHospitalDistance());          writer.newLine();
            writer.write("Status        : " + status);                                 writer.newLine();
            writer.write("Notes         : " + clean(request.getNotes()));              writer.newLine();
            writer.write("Date & Time   : " + date);                                   writer.newLine();
            writer.newLine();
        }

        // FILE HANDLING: appends one emergency request to the history file.
        // - FileWriter(FILE_NAME, true) opens the file in APPEND mode, so old
        //   records are kept and the new one is added at the end.
        // - BufferedWriter makes writing efficient.
        // - try-with-resources closes the writer automatically, even if an
        //   error occurs.
        // EXCEPTION HANDLING: an IOException (for example a locked or
        // read-only file) is caught and shown to the user in an error dialog
        // instead of crashing the program.
        static void saveRequest(EmergencyRequest request) {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(FILE_NAME, true))) {
                String status = "WAITING".equals(request.getAmbulanceId()) ? "Pending" : "In-Route";
                writeBlock(writer, request, status);
            } catch (IOException ex) {
                Dialogs.showMessageDialog(null,
                        "Could not save history file.\n" + ex.getMessage(),
                        "File Error", Dialogs.ERROR_MESSAGE);
            }
        }

        // FILE HANDLING: rewrites the whole file (append = false) so that a changed
        // status (Complete / Canceled) is stored permanently.
        static void saveAll(List<EmergencyRequest> all, Map<String, String> statuses) {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(FILE_NAME, false))) {
                for (EmergencyRequest r : all) {
                    String st = statuses.get(r.getId());
                    if (st == null) st = "WAITING".equals(r.getAmbulanceId()) ? "Pending" : "In-Route";
                    writeBlock(writer, r, st);
                }
            } catch (IOException ex) {
                Dialogs.showMessageDialog(null,
                        "Could not update history file.\n" + ex.getMessage(),
                        "File Error", Dialogs.ERROR_MESSAGE);
            }
        }

        // FILE HANDLING: reads the history file line by line and rebuilds the
        // EmergencyRequest objects. The saved status of each dispatch is put into
        // 'statuses'. Returns an empty list if the file does not exist yet.
        // EXCEPTION HANDLING: unreadable files / bad dates are caught, so a damaged
        // history file can never stop the program from starting.
        static List<EmergencyRequest> loadAll(Map<String, String> statuses) {
            List<EmergencyRequest> result = new ArrayList<EmergencyRequest>();
            File file = new File(FILE_NAME);
            if (!file.exists()) return result;

            Map<String, String> f = new HashMap<String, String>();
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("====")) {          // start of a new block
                        addParsed(f, result, statuses);
                        f.clear();
                        continue;
                    }
                    int idx = line.indexOf(':');
                    if (idx > 0) f.put(line.substring(0, idx).trim(), line.substring(idx + 1).trim());
                }
                addParsed(f, result, statuses);             // the last block
            } catch (IOException ex) {
                Dialogs.showMessageDialog(null,
                        "Could not read history file.\n" + ex.getMessage(),
                        "File Error", Dialogs.ERROR_MESSAGE);
            }
            return result;
        }

        // Builds one EmergencyRequest from the key/value pairs of one block.
        private static void addParsed(Map<String, String> f, List<EmergencyRequest> out,
                                      Map<String, String> statuses) {
            if (!f.containsKey("Dispatch ID") || !f.containsKey("Date & Time")) return;
            try {
                Date date = new SimpleDateFormat(DATE_FORMAT).parse(f.get("Date & Time"));

                String caller = firstNonEmpty(f, "Caller", "Caller Name", "Name", "Patient / Caller Name");
                String patient = firstNonEmpty(f, "Patient", "Patient Name", "Name", "Patient / Caller Name");
                if (caller.isEmpty()) caller = patient;
                if (patient.isEmpty()) patient = caller;
                if (caller.isEmpty()) { caller = "Unknown"; patient = "Unknown"; }

                EmergencyRequest r = new EmergencyRequest(f.get("Dispatch ID"),
                        caller, patient,
                        f.getOrDefault("Phone", ""), f.getOrDefault("Location", ""),
                        f.getOrDefault("Emergency", "General"), f.getOrDefault("Priority", "Normal"),
                        f.getOrDefault("Notes", ""), date);
                r.setAmbulanceId(f.getOrDefault("Ambulance", "WAITING"));
                r.setHospitalName(f.getOrDefault("Hospital", "Not Assigned"));
                try {
                    r.setHospitalDistance(Double.parseDouble(f.getOrDefault("Distance", "0")));
                } catch (NumberFormatException ignored) { }
                String st = f.get("Status");
                if (st != null && !st.isEmpty()) statuses.put(r.getId(), st);
                out.add(r);
            } catch (java.text.ParseException ex) {
                // a block with an invalid date is skipped
            }
        }
    }
    // Returns the first key that exists and has a non-empty value, else "".
    private static String firstNonEmpty(Map<String, String> f, String... keys) {
        for (String k : keys) {
            String v = f.get(k);
            if (v != null && !v.trim().isEmpty()) return v.trim();
        }
        return "";
    }
    // =========================================================
// FEATURE CARD (animated hover + click)
// INHERITANCE: extends JPanel.
// =========================================================
    static class FeatureCard extends JPanel {

        private static final int LIFT = 6;          // how many pixels the card rises
        private float progress = 0f;                // 0 = resting, 1 = fully hovered
        private Timer timer;
        private final String headText;
        private final JLabel head;
        private final JLabel body;

        FeatureCard(String headText, String bodyText, Runnable onClick) {
            this.headText = headText;

            setOpaque(false);
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBorder(new EmptyBorder(22, 18, 12, 18));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            head = new JLabel(headText);
            head.setForeground(RED);
            head.setFont(new Font("SansSerif", Font.BOLD, 13));

            body = new JLabel("<html>" + bodyText + "</html>");
            body.setForeground(MUTED);

            add(head);
            add(Box.createVerticalStrut(8));
            add(body);

            timer = new Timer(15, e -> {
                boolean inside = getMousePosition(true) != null;
                float target = inside ? 1f : 0f;

                progress += (target - progress) * 0.25f;
                if (Math.abs(target - progress) < 0.02f) {
                    progress = target;
                    ((Timer) e.getSource()).stop();
                }

                head.setText(progress > 0.5f ? headText + "  ›" : headText);
                body.setForeground(blend(MUTED, TEXT, progress));
                repaint();
            });

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) { timer.start(); }

                @Override
                public void mouseExited(MouseEvent e) { timer.start(); }

                @Override
                public void mouseClicked(MouseEvent e) {
                    if (onClick != null) onClick.run();
                }
            });
        }

        private static Color blend(Color a, Color b, float t) {
            int r = (int) (a.getRed() + (b.getRed() - a.getRed()) * t);
            int g = (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t);
            int bl = (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t);
            return new Color(r, g, bl);
        }

        // POLYMORPHISM: overrides paintComponent() to draw the card.
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth(), h = getHeight();
            int top = Math.round(LIFT * (1 - progress));   // card rises as progress grows

            // background
            Color bg = blend(PANEL, new Color(56, 26, 30), progress);
            g2.setColor(new Color(bg.getRed(), bg.getGreen(), bg.getBlue(), 235));
            g2.fillRoundRect(1, top, w - 3, h - top - 2, 14, 14);

            // red border, brighter and thicker on hover
            g2.setColor(new Color(245, 45, 45, (int) (60 + 195 * progress)));
            g2.setStroke(new BasicStroke(1f + 1.5f * progress));
            g2.drawRoundRect(1, top, w - 3, h - top - 2, 14, 14);

            // red line growing from the center along the bottom
            if (progress > 0f) {
                int lineW = (int) ((w - 30) * progress);
                g2.setColor(RED);
                g2.fillRoundRect((w - lineW) / 2, h - 6, lineW, 3, 3, 3);
            }

            g2.dispose();
        }

        // POLYMORPHISM: overrides paintChildren() so the text rises together
        // with the card.
        @Override
        protected void paintChildren(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.translate(0, -LIFT * progress);              // text rises with the card
            super.paintChildren(g2);
            g2.dispose();
        }
    }
    // =========================================================
// ANIMATED CARD (pop-in animation + glow)
// INHERITANCE: AnimatedCard extends HoverPanel, which extends JPanel
// (multi-level inheritance). It reuses the hover behaviour of HoverPanel
// and adds the pop-in animation and glow.
// =========================================================
    static class AnimatedCard extends HoverPanel {

        private float progress = 1f;
        private Timer delayTimer;
        private Timer animTimer;

        // glow
        private float glowPhase = 0f;
        private float hoverGlow = 0f;
        private boolean hoverIn = false;
        private final Timer glowTimer;

        AnimatedCard() {
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) { hoverIn = true; }

                @Override
                public void mouseExited(MouseEvent e) { hoverIn = false; }
            });

            glowTimer = new Timer(30, e -> {
                glowPhase += 0.08f;
                hoverGlow += ((hoverIn ? 1f : 0f) - hoverGlow) * 0.2f;
                repaint();
            });
        }

        @Override
        public void addNotify() {
            super.addNotify();
            glowTimer.start();
        }

        @Override
        public void removeNotify() {
            glowTimer.stop();
            super.removeNotify();
        }

        void play(int delayMs) {
            if (delayTimer != null) delayTimer.stop();
            if (animTimer != null) animTimer.stop();

            progress = 0f;
            repaint();

            delayTimer = new Timer(delayMs, e -> {
                animTimer = new Timer(16, ev -> {
                    progress += 0.06f;
                    if (progress >= 1f) {
                        progress = 1f;
                        ((Timer) ev.getSource()).stop();
                    }
                    repaint();
                });
                animTimer.start();
            });
            delayTimer.setRepeats(false);
            delayTimer.start();
        }

        private void paintGlow(Graphics2D g2) {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            float pulse = 0.5f + 0.5f * (float) Math.sin(glowPhase);
            float strength = 0.35f + 0.25f * pulse + 0.40f * hoverGlow;

            int w = getWidth(), h = getHeight();
            int layers = 6;

            // soft inner glow: rings fade out toward the center
            g2.setStroke(new BasicStroke(2f));
            for (int i = 0; i < layers; i++) {
                int a = (int) (strength * 80 * (1f - i / (float) layers));
                a = Math.max(0, Math.min(255, a));
                g2.setColor(new Color(245, 45, 45, a));
                g2.drawRoundRect(i * 2, i * 2, w - 1 - i * 4, h - 1 - i * 4, 8, 8);
            }

            // bright edge line
            int edge = Math.max(0, Math.min(255, (int) (strength * 200)));
            g2.setStroke(new BasicStroke(1.2f));
            g2.setColor(new Color(255, 80, 80, edge));
            g2.drawRoundRect(0, 0, w - 1, h - 1, 8, 8);
        }

        // POLYMORPHISM: overrides paint() from the parent. It calls
        // super.paint() to reuse the parent's drawing, then adds the glow
        // and the pop-in scale effect.
        @Override
        public void paint(Graphics g) {
            if (progress >= 1f) {
                super.paint(g);
                Graphics2D g2 = (Graphics2D) g.create();
                paintGlow(g2);
                g2.dispose();
                return;
            }

            float ease = 1f - (float) Math.pow(1f - progress, 3);
            float scale = 0.85f + 0.15f * ease;

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setComposite(AlphaComposite.SrcOver.derive(Math.min(1f, ease)));

            double cx = getWidth() / 2.0, cy = getHeight() / 2.0;
            g2.translate(cx, cy + (1f - ease) * 18);
            g2.scale(scale, scale);
            g2.translate(-cx, -cy);

            super.paint(g2);
            paintGlow(g2);
            g2.dispose();
        }
    }
    // =========================================================
// BED CARD (pop-in + grow on hover + green glow)
// INHERITANCE: BedCard extends HoverPanel. It is a sibling of AnimatedCard:
// both reuse HoverPanel but draw different effects (green vs red glow).
// =========================================================
    static class BedCard extends HoverPanel {

        private float progress = 1f;
        private float hover = 0f;
        private float glowPhase = 0f;
        private Timer delayTimer;
        private Timer animTimer;
        private final Timer tickTimer;

        BedCard() {
            setOpaque(false);

            tickTimer = new Timer(30, e -> {
                glowPhase += 0.08f;
                float target = getMousePosition(true) != null ? 1f : 0f;
                hover += (target - hover) * 0.2f;
                repaint();
            });
        }

        @Override
        public void addNotify() {
            super.addNotify();
            tickTimer.start();
        }

        @Override
        public void removeNotify() {
            tickTimer.stop();
            super.removeNotify();
        }

        void play(int delayMs) {
            if (delayTimer != null) delayTimer.stop();
            if (animTimer != null) animTimer.stop();

            progress = 0f;
            repaint();

            delayTimer = new Timer(delayMs, e -> {
                animTimer = new Timer(16, ev -> {
                    progress += 0.06f;
                    if (progress >= 1f) {
                        progress = 1f;
                        ((Timer) ev.getSource()).stop();
                    }
                    repaint();
                });
                animTimer.start();
            });
            delayTimer.setRepeats(false);
            delayTimer.start();
        }

        // POLYMORPHISM: overrides HoverPanel's paintComponent() with its own
        // background that fades towards green on hover.
        @Override
        protected void paintComponent(Graphics g) {
            Color hoverBg = new Color(24, 38, 32);
            int r = (int) (PANEL.getRed() + (hoverBg.getRed() - PANEL.getRed()) * hover);
            int gr = (int) (PANEL.getGreen() + (hoverBg.getGreen() - PANEL.getGreen()) * hover);
            int b = (int) (PANEL.getBlue() + (hoverBg.getBlue() - PANEL.getBlue()) * hover);
            g.setColor(new Color(r, gr, b));
            g.fillRect(0, 0, getWidth(), getHeight());
        }

        private void paintGlow(Graphics2D g2) {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            float pulse = 0.5f + 0.5f * (float) Math.sin(glowPhase);
            float strength = 0.30f + 0.25f * pulse + 0.50f * hover;

            int w = getWidth(), h = getHeight();
            int layers = 6;

            g2.setStroke(new BasicStroke(2f));
            for (int i = 0; i < layers; i++) {
                int a = (int) (strength * 80 * (1f - i / (float) layers));
                a = Math.max(0, Math.min(255, a));
                g2.setColor(new Color(70, 210, 125, a));
                g2.drawRoundRect(i * 2, i * 2, w - 1 - i * 4, h - 1 - i * 4, 8, 8);
            }

            int edge = Math.max(0, Math.min(255, (int) (strength * 200)));
            g2.setStroke(new BasicStroke(1.2f));
            g2.setColor(new Color(110, 235, 150, edge));
            g2.drawRoundRect(0, 0, w - 1, h - 1, 8, 8);
        }

        // POLYMORPHISM: overrides paint() to add the pop-in, hover-grow and
        // green glow effects on top of the parent's drawing.
        @Override
        public void paint(Graphics g) {
            float ease = 1f - (float) Math.pow(1f - progress, 3);
            // rests at 94% size, grows to 100% on hover, pop-in multiplies on top
            float scale = (0.94f + 0.06f * hover) * (0.85f + 0.15f * ease);

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setComposite(AlphaComposite.SrcOver.derive(Math.min(1f, ease)));

            double cx = getWidth() / 2.0, cy = getHeight() / 2.0;
            g2.translate(cx, cy + (1f - ease) * 18);
            g2.scale(scale, scale);
            g2.translate(-cx, -cy);

            super.paint(g2);
            paintGlow(g2);
            g2.dispose();
        }
    }
    // =========================================================
    // HOVER PANEL
    // INHERITANCE: HoverPanel extends JPanel. It is the parent class of
    // AnimatedCard and BedCard, which inherit its hover behaviour.
    // ENCAPSULATION: the "hovered" flag is private.
    // =========================================================
    static class HoverPanel extends JPanel {

        private boolean hovered = false;

        HoverPanel() {
            setBackground(PANEL);
            setBorder(BorderFactory.createLineBorder(new Color(55, 60, 65)));

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hovered = true;
                    setBackground(new Color(38, 28, 31));
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hovered = false;
                    setBackground(PANEL);
                    repaint();
                }
            });
        }

        // POLYMORPHISM: overrides paintComponent() to add a red tint while
        // hovered. Subclasses override it again with their own drawing.
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (hovered) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(new Color(245, 45, 45, 30));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.dispose();
            }
        }
    }

    // =========================================================
    // ROUNDED BUTTON
    // INHERITANCE: RoundedButton extends JButton, so it behaves like a normal
    // button (clicks, listeners) but is drawn with rounded corners.
    // ENCAPSULATION: private fields hide the colour and hover state.
    // =========================================================
    static class RoundedButton extends JButton {

        private final boolean redButton;
        private boolean hover = false;

        RoundedButton(String text, boolean redButton) {
            super(text);
            this.redButton = redButton;

            setForeground(Color.WHITE);
            setFont(new Font("SansSerif", Font.BOLD, 12));
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setBorder(new EmptyBorder(11, 17, 11, 17));

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    repaint();
                }
            });
        }

        // POLYMORPHISM: overrides paintComponent() to draw a rounded
        // background, then calls super.paintComponent() to draw the text.
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            Color fill;
            if (redButton) {
                fill = hover ? new Color(255, 80, 80) : RED;
            } else {
                fill = hover ? new Color(60, 66, 72) : new Color(36, 42, 48);
            }

            g2.setColor(fill);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
            g2.dispose();

            super.paintComponent(g);
        }
    }

    // =========================================================
    // LOGIN PAGE
    // INHERITANCE: LoginPage extends JPanel.
    // ENCAPSULATION: credentials and the success callback are private.
    // =========================================================
    public class LoginPage extends JPanel {

        // ===== THEME (taken from Main) =====
        private static final Color BG = Main.BG;
        private static final Color PANEL = Main.PANEL;
        private static final Color RED = Main.RED;
        private static final Color TEXT = Main.TEXT;
        private static final Color MUTED = Main.MUTED;

        // ===== DEMO CREDENTIALS (change these as you like) =====
        private static final String USERNAME = "admin";
        private static final String PASSWORD = "admin123";

        private final Runnable onLoginSuccess;
        private Image bgImage;
        private int attempts = 0;

        public LoginPage(Runnable onLoginSuccess) {
            this.onLoginSuccess = onLoginSuccess;

            setBackground(BG);
            setLayout(new GridBagLayout());

            bgImage = loadImage("images/login_bg.png");
            if (bgImage == null) bgImage = loadImage("images/home_bg.png");

            JPanel content = new JPanel();
            content.setOpaque(false);
            content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
            content.setBorder(new EmptyBorder(16, 40, 16, 40));

            // ---------- badge ----------
            // INHERITANCE + POLYMORPHISM: anonymous subclass of JLabel that
            // overrides paintComponent() to draw a rounded pill background.
            JLabel badge = new JLabel("Powered by MediRoute Smart Network", SwingConstants.CENTER) {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(new Color(40, 44, 52, 230));
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
                    g2.dispose();
                    super.paintComponent(g);
                }
            };
            badge.setOpaque(false);
            badge.setForeground(TEXT);
            badge.setFont(new Font("SansSerif", Font.BOLD, 13));
            badge.setBorder(new EmptyBorder(8, 20, 8, 20));
            badge.setAlignmentX(Component.CENTER_ALIGNMENT);

            // ---------- heading ----------
            JLabel heading = new JLabel("<html><div style='text-align:center'>Make every "
                    + "<span style='color:#F52D2D'>minute</span> count</div></html>", SwingConstants.CENTER);
            heading.setForeground(TEXT);
            heading.setFont(new Font("SansSerif", Font.BOLD, 46));
            heading.setAlignmentX(Component.CENTER_ALIGNMENT);

            JLabel sub = new JLabel("<html><div style='text-align:center'>Dispatch ambulances, track routes live, "
                    + "find hospital beds and guide paramedics<br>with tele-care, all in one smart emergency network."
                    + "</div></html>", SwingConstants.CENTER);
            sub.setForeground(new Color(215, 220, 225));
            sub.setFont(new Font("SansSerif", Font.BOLD, 15));
            sub.setAlignmentX(Component.CENTER_ALIGNMENT);

            // ---------- LOG IN button ----------
            JButton login = new Main.RoundedButton("LOG IN", true);
            login.setFont(new Font("SansSerif", Font.BOLD, 14));
            Dimension bs = new Dimension(170, 46);
            login.setPreferredSize(bs);
            login.setMaximumSize(bs);
            login.setAlignmentX(Component.CENTER_ALIGNMENT);
            login.addActionListener(e -> showLoginDialog());

            // ---------- story cards ----------
            JPanel row = new JPanel(new GridLayout(1, 3, 22, 0));
            row.setOpaque(false);
            Dimension rs = new Dimension(1020, 335);
            row.setPreferredSize(rs);
            row.setMaximumSize(rs);
            row.setAlignmentX(Component.CENTER_ALIGNMENT);

            row.add(new StoryCard("SMART ROUTING",
                    "Live traffic-aware routes guide every ambulance to the patient and hospital by the fastest path.",
                    new String[]{"images/story_routing_1.png", "images/story_routing_2.png"},
                    new String[]{"Live route to patient", "Re-routing around traffic"},
                    new Color[]{new Color(40, 90, 200), new Color(30, 150, 160)}));

            row.add(new StoryCard("BED AVAILABILITY",
                    "See real-time beds across Emergency, ICU and Trauma wards before the ambulance arrives.",
                    new String[]{"images/city_emergency.png", "images/lifeline.png"},
                    new String[]{"City Emergency Hospital", "LifeLine Medical Center"},
                    new Color[]{new Color(40, 140, 90), new Color(150, 100, 30)}));

            row.add(new StoryCard("TELE-CARE",
                    "Doctors guide paramedics remotely during transit with live vitals and a treatment log.",
                    new String[]{"images/story_telecare_1.png", "images/story_telecare_2.png"},
                    new String[]{"Live patient vitals", "Doctor-guided treatment"},
                    new Color[]{new Color(170, 40, 50), new Color(120, 50, 140)}));

            content.add(badge);
            content.add(Box.createVerticalStrut(16));
            content.add(heading);
            content.add(Box.createVerticalStrut(10));
            content.add(sub);
            content.add(Box.createVerticalStrut(18));
            content.add(login);
            content.add(Box.createVerticalStrut(26));
            content.add(row);

            add(content, new GridBagConstraints());
        }

        // =========================================================
        // BACKGROUND
        // =========================================================
        // POLYMORPHISM: overrides paintComponent() to draw the background.
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) {
                g2.dispose();
                return;
            }

            if (bgImage != null) {
                int iw = bgImage.getWidth(null), ih = bgImage.getHeight(null);
                if (iw > 0 && ih > 0) {
                    double scale = Math.max((double) w / iw, (double) h / ih);
                    int dw = (int) (iw * scale), dh = (int) (ih * scale);
                    g2.drawImage(bgImage, (w - dw) / 2, (h - dh) / 2, dw, dh, null);
                }
                g2.setColor(new Color(10, 14, 18, 205));
                g2.fillRect(0, 0, w, h);
            } else {
                g2.setPaint(new GradientPaint(0, 0, new Color(16, 20, 26), 0, h, new Color(8, 10, 14)));
                g2.fillRect(0, 0, w, h);
            }

            // soft red glow behind the heading
            g2.setPaint(new RadialGradientPaint(new Point(w / 2, h / 4), Math.max(w, h) * 0.45f,
                    new float[]{0f, 1f},
                    new Color[]{new Color(245, 45, 45, 45), new Color(245, 45, 45, 0)}));
            g2.fillRect(0, 0, w, h);
            g2.dispose();
        }

        // FILE HANDLING + EXCEPTION HANDLING: reads an image file from disk.
        // If the file is missing or unreadable, the exception is caught and
        // null is returned so the page falls back to a plain background.
        private static BufferedImage loadImage(String path) {
            try {
                File f = Main.resolve(path);
                if (!f.exists()) return null;
                return ImageIO.read(f);
            } catch (Exception ex) {
                return null;
            }
        }

        // =========================================================
        // LOGIN DIALOG
        // =========================================================
        // Validates the username and password. Empty or wrong input shows an
        // error message and the dialog stays open; correct input closes the
        // dialog and runs the onLoginSuccess callback.
        private void showLoginDialog() {
            final JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this),
                    "MediRoute - Log In", Dialog.ModalityType.APPLICATION_MODAL);
            dialog.setSize(430, 420);
            dialog.setResizable(false);
            dialog.setLocationRelativeTo(this);

            JPanel main = new JPanel();
            main.setBackground(BG);
            main.setLayout(new BoxLayout(main, BoxLayout.Y_AXIS));
            main.setBorder(new EmptyBorder(28, 36, 24, 36));

            JLabel title = new JLabel("<html>Medi<span style='color:#F52D2D'>Route</span> Log In</html>");
            title.setForeground(TEXT);
            title.setFont(new Font("SansSerif", Font.BOLD, 26));
            title.setAlignmentX(Component.LEFT_ALIGNMENT);

            JLabel sub = new JLabel("Enter your credentials to open the dashboard.");
            sub.setForeground(MUTED);
            sub.setFont(new Font("SansSerif", Font.PLAIN, 12));
            sub.setAlignmentX(Component.LEFT_ALIGNMENT);

            final JTextField userField = new JTextField();
            final JPasswordField passField = new JPasswordField();
            styleField(userField);
            styleField(passField);

            final JLabel error = new JLabel(" ");
            error.setForeground(RED);
            error.setFont(new Font("SansSerif", Font.BOLD, 12));
            error.setAlignmentX(Component.LEFT_ALIGNMENT);

            JLabel hint = new JLabel("Demo account:  " + USERNAME + " / " + PASSWORD);
            hint.setForeground(MUTED);
            hint.setFont(new Font("SansSerif", Font.PLAIN, 11));
            hint.setAlignmentX(Component.LEFT_ALIGNMENT);

            final JButton ok = new Main.RoundedButton("LOG IN", true);   // declared first so attempt can use it

            final Runnable attempt = () -> {
                if (!ok.isEnabled()) return;                              // locked out

                String u = userField.getText().trim();
                char[] pc = passField.getPassword();
                String p = new String(pc);
                java.util.Arrays.fill(pc, '\0');                          // wipe the char[] copy

                if (u.isEmpty() || p.isEmpty()) {
                    error.setText("Please enter username and password.");
                    return;
                }
                if (!USERNAME.equals(u) || !PASSWORD.equals(p)) {
                    passField.setText("");
                    attempts++;
                    if (attempts >= 3) {
                        error.setText("Too many attempts. Wait 10 seconds.");
                        ok.setEnabled(false);
                        Timer unlock = new Timer(10000, ev -> {
                            ok.setEnabled(true);
                            attempts = 0;
                            error.setText(" ");
                        });
                        unlock.setRepeats(false);
                        unlock.start();
                    } else {
                        error.setText("Incorrect username or password. (" + (3 - attempts) + " left)");
                    }
                    return;
                }
                attempts = 0;
                dialog.dispose();
                onLoginSuccess.run();
            };
            userField.addActionListener(e -> attempt.run());
            passField.addActionListener(e -> attempt.run());

            JButton cancel = new Main.RoundedButton("CANCEL", false);
            cancel.addActionListener(e -> dialog.dispose());
            ok.addActionListener(e -> attempt.run());

            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            buttons.setOpaque(false);
            buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
            buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
            buttons.add(cancel);
            buttons.add(ok);

            main.add(title);
            main.add(Box.createVerticalStrut(6));
            main.add(sub);
            main.add(Box.createVerticalStrut(22));
            main.add(fieldLabel("Username"));
            main.add(Box.createVerticalStrut(6));
            main.add(userField);
            main.add(Box.createVerticalStrut(14));
            main.add(fieldLabel("Password"));
            main.add(Box.createVerticalStrut(6));
            main.add(passField);
            main.add(Box.createVerticalStrut(10));
            main.add(error);
            main.add(Box.createVerticalStrut(2));
            main.add(hint);
            main.add(Box.createVerticalGlue());
            main.add(buttons);

            dialog.setContentPane(main);
            dialog.getRootPane().setDefaultButton(ok);
            dialog.setVisible(true);
        }

        private JLabel fieldLabel(String text) {
            JLabel l = new JLabel(text);
            l.setForeground(TEXT);
            l.setFont(new Font("SansSerif", Font.BOLD, 13));
            l.setAlignmentX(Component.LEFT_ALIGNMENT);
            return l;
        }

        private void styleField(JTextField f) {
            f.setBackground(new Color(31, 36, 42));
            f.setForeground(TEXT);
            f.setCaretColor(TEXT);
            f.setFont(new Font("SansSerif", Font.PLAIN, 14));
            f.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(55, 60, 65)),
                    new EmptyBorder(9, 10, 9, 10)));
            f.setAlignmentX(Component.LEFT_ALIGNMENT);
            f.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        }

        // =========================================================
        // STORY CARD (hover lift + glow, image slider inside)
        // INHERITANCE: StoryCard extends JPanel.
        // =========================================================
        private class StoryCard extends JPanel {

            private static final int LIFT = 8;
            private float progress = 0f;
            private final Timer hoverTimer;

            StoryCard(String headText, String bodyText, String[] paths, String[] captions, Color[] tints) {
                setOpaque(false);
                setLayout(new BorderLayout(0, 10));
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                applyBorder();

                ImagePanel images = new ImagePanel(paths, captions, tints);
                images.setPreferredSize(new Dimension(100, 160));

                JLabel head = new JLabel(headText);
                head.setForeground(RED);
                head.setFont(new Font("SansSerif", Font.BOLD, 14));
                head.setAlignmentX(Component.LEFT_ALIGNMENT);

                JLabel body = new JLabel("<html><div style='width:250px'>" + bodyText + "</div></html>");
                body.setForeground(MUTED);
                body.setFont(new Font("SansSerif", Font.PLAIN, 12));
                body.setAlignmentX(Component.LEFT_ALIGNMENT);

                JPanel text = new JPanel();
                text.setOpaque(false);
                text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
                text.add(head);
                text.add(Box.createVerticalStrut(6));
                text.add(body);

                JButton tryIt = new Main.RoundedButton("TRY IT NOW", true);
                tryIt.addActionListener(e -> showLoginDialog());
                JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
                btnRow.setOpaque(false);
                btnRow.add(tryIt);

                add(images, BorderLayout.NORTH);
                add(text, BorderLayout.CENTER);
                add(btnRow, BorderLayout.SOUTH);

                hoverTimer = new Timer(20, e -> {
                    float target = getMousePosition(true) != null ? 1f : 0f;
                    float next = progress + (target - progress) * 0.25f;
                    if (Math.abs(target - next) < 0.01f) next = target;
                    if (next != progress) {
                        progress = next;
                        applyBorder();
                        revalidate();
                        repaint();
                    }
                });
            }

            // moves the content up/down together with the card background
            private void applyBorder() {
                int top = 14 + Math.round(LIFT * (1 - progress));
                int bottom = 16 + Math.round(LIFT * progress);
                setBorder(new EmptyBorder(top, 14, bottom, 14));
            }

            @Override
            public void addNotify() {
                super.addNotify();
                hoverTimer.start();
            }

            @Override
            public void removeNotify() {
                hoverTimer.stop();
                super.removeNotify();
            }

            private Color blend(Color a, Color b, float t) {
                return new Color(
                        (int) (a.getRed() + (b.getRed() - a.getRed()) * t),
                        (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                        (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t));
            }

            // POLYMORPHISM: overrides paintComponent() to draw the card.
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int w = getWidth(), h = getHeight();
                int y0 = Math.round(LIFT * (1 - progress));
                int ch = h - LIFT - 2;

                // soft red glow that grows on hover
                for (int i = 0; i < 5; i++) {
                    int a = (int) (progress * 34 * (1f - i / 5f));
                    g2.setColor(new Color(245, 45, 45, a));
                    g2.fillRoundRect(1 - i, y0 - i, w - 3 + i * 2, ch + i * 2, 18 + i * 2, 18 + i * 2);
                }

                Color bg = blend(PANEL, new Color(56, 26, 30), progress);
                g2.setColor(new Color(bg.getRed(), bg.getGreen(), bg.getBlue(), 240));
                g2.fillRoundRect(1, y0, w - 3, ch, 16, 16);

                g2.setColor(new Color(245, 45, 45, (int) (60 + 195 * progress)));
                g2.setStroke(new BasicStroke(1f + 1.5f * progress));
                g2.drawRoundRect(1, y0, w - 3, ch, 16, 16);

                // red line growing from the centre along the bottom edge
                if (progress > 0f) {
                    int lineW = (int) ((w - 40) * progress);
                    g2.setColor(RED);
                    g2.fillRoundRect((w - lineW) / 2, y0 + ch - 3, lineW, 3, 3, 3);
                }
                g2.dispose();
            }
        }

        // =========================================================
        // IMAGE SLIDER (2+ pictures, arrows with slide animation)
        // INHERITANCE: ImagePanel extends JComponent.
        // ENCAPSULATION: images, index and animation state are private.
        // =========================================================
        private class ImagePanel extends JComponent {

            private final List<Image> images = new ArrayList<Image>();
            private final String[] captions;
            private final Color[] tints;

            private int index = 0, prev = 0, dir = 1;
            private float t = 1f;
            private Timer anim;
            private boolean hoverLeft = false, hoverRight = false;

            ImagePanel(String[] paths, String[] captions, Color[] tints) {
                this.captions = captions;
                this.tints = tints;
                for (String p : paths) images.add(loadImage(p));   // null -> drawn placeholder

                MouseAdapter ma = new MouseAdapter() {
                    @Override
                    public void mouseMoved(MouseEvent e) {
                        boolean l = leftRect().contains(e.getPoint());
                        boolean r = rightRect().contains(e.getPoint());
                        if (l != hoverLeft || r != hoverRight) {
                            hoverLeft = l;
                            hoverRight = r;
                            repaint();
                        }
                        setCursor(Cursor.getPredefinedCursor(l || r ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
                    }

                    @Override
                    public void mouseExited(MouseEvent e) {
                        hoverLeft = false;
                        hoverRight = false;
                        repaint();
                    }

                    @Override
                    public void mouseClicked(MouseEvent e) {
                        if (leftRect().contains(e.getPoint())) go(-1);
                        else if (rightRect().contains(e.getPoint())) go(1);
                    }
                };
                addMouseListener(ma);
                addMouseMotionListener(ma);
            }

            private Rectangle leftRect() {
                return new Rectangle(getWidth() - 86, getHeight() - 44, 36, 32);
            }

            private Rectangle rightRect() {
                return new Rectangle(getWidth() - 50, getHeight() - 44, 36, 32);
            }

            private void go(int d) {
                int n = images.size();
                prev = index;
                index = (index + d + n) % n;
                dir = d;
                t = 0f;
                if (anim != null) anim.stop();
                anim = new Timer(16, e -> {
                    t += 0.08f;
                    if (t >= 1f) {
                        t = 1f;
                        ((Timer) e.getSource()).stop();
                    }
                    repaint();
                });
                anim.start();
            }

            // POLYMORPHISM: overrides paintComponent() to draw the slider.
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

                int w = getWidth(), h = getHeight();
                g2.clip(new RoundRectangle2D.Float(0, 0, w, h, 14, 14));

                float e = 1f - (float) Math.pow(1f - t, 3);
                if (t < 1f) {
                    drawSlide(g2, prev, (int) (-dir * e * w), w, h);
                    drawSlide(g2, index, (int) (dir * (1f - e) * w), w, h);
                } else {
                    drawSlide(g2, index, 0, w, h);
                }

                // darker strip at the bottom so the controls stay readable
                g2.setPaint(new GradientPaint(0, h - 56, new Color(0, 0, 0, 0), 0, h, new Color(0, 0, 0, 140)));
                g2.fillRect(0, h - 56, w, 56);

                // dots
                for (int i = 0; i < images.size(); i++) {
                    g2.setColor(i == index ? Color.WHITE : new Color(255, 255, 255, 110));
                    g2.fillOval(14 + i * 14, h - 24, 8, 8);
                }

                // arrow pill
                g2.setColor(new Color(0, 0, 0, 120));
                g2.fillRoundRect(w - 90, h - 46, 80, 36, 36, 36);
                drawArrow(g2, leftRect(), true, hoverLeft);
                drawArrow(g2, rightRect(), false, hoverRight);

                g2.dispose();
            }

            private void drawArrow(Graphics2D g2, Rectangle r, boolean left, boolean hover) {
                if (hover) {
                    g2.setColor(new Color(245, 45, 45, 220));
                    g2.fillRoundRect(r.x, r.y + 1, r.width, r.height - 2, 30, 30);
                }
                int cx = r.x + r.width / 2, cy = r.y + r.height / 2;
                int s = left ? 1 : -1;
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawPolyline(new int[]{cx + 3 * s, cx - 3 * s, cx + 3 * s},
                        new int[]{cy - 6, cy, cy + 6}, 3);
            }

            private void drawSlide(Graphics2D g2, int i, int xOff, int w, int h) {
                Graphics2D s = (Graphics2D) g2.create();
                s.translate(xOff, 0);
                Image im = images.get(i);
                int iw = im == null ? 0 : im.getWidth(null);
                int ih = im == null ? 0 : im.getHeight(null);

                if (im != null && iw > 0 && ih > 0) {
                    double scale = Math.max((double) w / iw, (double) h / ih);   // cover
                    int dw = (int) (iw * scale), dh = (int) (ih * scale);
                    s.drawImage(im, (w - dw) / 2, (h - dh) / 2, dw, dh, null);
                } else {
                    // placeholder shown when the image file is missing
                    Color c = tints[i % tints.length];
                    s.setPaint(new GradientPaint(0, 0, c, w, h, new Color(10, 14, 18)));
                    s.fillRect(0, 0, w, h);
                    s.setColor(new Color(255, 255, 255, 30));
                    s.fillOval(w / 2 - 48, h / 2 - 62, 96, 96);
                    s.setColor(new Color(255, 255, 255, 215));
                    s.fillRoundRect(w / 2 - 6, h / 2 - 42, 12, 56, 4, 4);
                    s.fillRoundRect(w / 2 - 28, h / 2 - 20, 56, 12, 4, 4);
                    s.setFont(new Font("SansSerif", Font.BOLD, 13));
                    String cap = captions[i % captions.length];
                    FontMetrics fm = s.getFontMetrics();
                    s.drawString(cap, (w - fm.stringWidth(cap)) / 2, h / 2 + 46);
                }
                s.dispose();
            }
        }
    }

    // =========================================================
    // THEMED DIALOGS (black + red)
    // [NEW] Earlier, JOptionPane's white/grey pop-ups were used.
    // Now every pop-up (message / Yes-No) is built by this class, which uses the
    // app's black + red theme. Method names are kept the same as JOptionPane's,
    // so the rest of the code only had to replace "JOptionPane." with
    // "Dialogs.".
    // =========================================================
    static class Dialogs {

        // same values as JOptionPane so old code keeps working
        static final int YES_NO_OPTION = 0;
        static final int YES_OPTION = 0;
        static final int NO_OPTION = 1;
        static final int ERROR_MESSAGE = 0;
        static final int INFORMATION_MESSAGE = 1;
        static final int WARNING_MESSAGE = 2;
        static final int QUESTION_MESSAGE = 3;

        // Simple message with an OK button.
        static void showMessageDialog(Component parent, Object message) {
            showMessageDialog(parent, message, "MediRoute", INFORMATION_MESSAGE);
        }

        static void showMessageDialog(Component parent, Object message, String title, int type) {
            show(parent, String.valueOf(message), title, type, false);
        }

        // Yes / No question. Returns YES_OPTION or NO_OPTION.
        static int showConfirmDialog(Component parent, Object message, String title, int optionType) {
            return show(parent, String.valueOf(message), title, QUESTION_MESSAGE, true);
        }

        // Builds and shows the dialog. Returns 0 (Yes/OK) or 1 (No/closed).
        private static int show(Component parent, String message, String title, int type, boolean yesNo) {
            Window owner = parent == null ? null
                    : (parent instanceof Window ? (Window) parent : SwingUtilities.getWindowAncestor(parent));
            final JDialog dialog = new JDialog(owner, title, Dialog.ModalityType.APPLICATION_MODAL);
            final int[] result = {NO_OPTION};
            dialog.setUndecorated(true);                 // we draw our own dark title bar
            dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

            // ----- outer frame: black background + red border -----
            JPanel root = new JPanel(new BorderLayout());
            root.setBackground(BG);
            root.setBorder(BorderFactory.createLineBorder(RED, 2));

            // ----- title strip -----
            JLabel head = new JLabel(title.toUpperCase());
            head.setOpaque(true);
            head.setBackground(new Color(30, 10, 12));
            head.setForeground(RED);
            head.setFont(new Font("SansSerif", Font.BOLD, 12));
            head.setBorder(new EmptyBorder(9, 16, 9, 16));
            root.add(head, BorderLayout.NORTH);

            // ----- body: round icon + message text -----
            JPanel body = new JPanel(new BorderLayout(16, 0));
            body.setBackground(PANEL);
            body.setBorder(new EmptyBorder(22, 22, 14, 26));

            final String symbol = type == QUESTION_MESSAGE ? "?" : (type == INFORMATION_MESSAGE ? "i" : "!");
            JComponent icon = new JComponent() {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    g2.setColor(RED);
                    g2.fillOval(2, 2, 40, 40);
                    g2.setColor(Color.WHITE);
                    g2.setFont(new Font("SansSerif", Font.BOLD, 24));
                    FontMetrics fm = g2.getFontMetrics();
                    g2.drawString(symbol, 22 - fm.stringWidth(symbol) / 2, 22 + fm.getAscent() / 2 - 2);
                    g2.dispose();
                }
            };
            icon.setPreferredSize(new Dimension(46, 46));
            JPanel iconWrap = new JPanel(new BorderLayout());
            iconWrap.setOpaque(false);
            iconWrap.add(icon, BorderLayout.NORTH);
            body.add(iconWrap, BorderLayout.WEST);

            // message: turn "\n" into <br> and escape HTML special characters
            String html = message.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                    .replace("\n", "<br>");
            JLabel text = new JLabel("<html><div style='width:330px'>" + html + "</div></html>");
            text.setForeground(TEXT);
            text.setFont(new Font("SansSerif", Font.BOLD, 13));
            body.add(text, BorderLayout.CENTER);
            root.add(body, BorderLayout.CENTER);

            // ----- buttons -----
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
            buttons.setBackground(PANEL);
            if (yesNo) {
                JButton no = new RoundedButton("No", false);
                no.addActionListener(e -> { result[0] = NO_OPTION; dialog.dispose(); });
                JButton yes = new RoundedButton("Yes", true);
                yes.addActionListener(e -> { result[0] = YES_OPTION; dialog.dispose(); });
                buttons.add(no);
                buttons.add(yes);
                dialog.getRootPane().setDefaultButton(yes);
            } else {
                JButton ok = new RoundedButton("OK", true);
                ok.addActionListener(e -> { result[0] = YES_OPTION; dialog.dispose(); });
                buttons.add(ok);
                dialog.getRootPane().setDefaultButton(ok);
            }
            root.add(buttons, BorderLayout.SOUTH);

            // ESC key = close (counts as "No")
            dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(),
                    KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

            dialog.setContentPane(root);
            dialog.pack();
            dialog.setLocationRelativeTo(parent);
            dialog.setVisible(true);                     // blocks until closed (modal)
            return result[0];
        }
    }

    // =========================================================
    // MAIN
    // =========================================================
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) { }

        // [NEW] The remaining small white parts (tooltip, drop-down list) also follow the theme.
        UIManager.put("ToolTip.background", new Color(20, 25, 30));
        UIManager.put("ToolTip.foreground", TEXT);
        UIManager.put("ToolTip.border", BorderFactory.createLineBorder(RED));
        UIManager.put("ComboBox.background", new Color(31, 36, 42));
        UIManager.put("ComboBox.foreground", TEXT);
        UIManager.put("ComboBox.selectionBackground", RED);
        UIManager.put("ComboBox.selectionForeground", Color.WHITE);
        UIManager.put("List.background", new Color(31, 36, 42));
        UIManager.put("List.foreground", TEXT);
        UIManager.put("List.selectionBackground", RED);
        UIManager.put("List.selectionForeground", Color.WHITE);
        SwingUtilities.invokeLater(() -> new Main().setVisible(true));
    }
}