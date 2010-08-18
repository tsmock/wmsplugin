package wmsplugin;

import static org.openstreetmap.josm.gui.help.HelpUtil.ht;
import static org.openstreetmap.josm.tools.I18n.marktr;
import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.swing.JMenu;
import javax.swing.JMenuItem;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.ExtensionFileFilter;
import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.data.ProjectionBounds;
import org.openstreetmap.josm.gui.IconToggleButton;
import org.openstreetmap.josm.gui.MainMenu;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.preferences.PreferenceSetting;
import org.openstreetmap.josm.io.CacheFiles;
import org.openstreetmap.josm.io.MirroredInputStream;
import org.openstreetmap.josm.plugins.Plugin;
import org.openstreetmap.josm.plugins.PluginHandler;
import org.openstreetmap.josm.plugins.PluginInformation;
import org.openstreetmap.josm.plugins.PluginProxy;

import wmsplugin.io.WMSLayerExporter;
import wmsplugin.io.WMSLayerImporter;

public class WMSPlugin extends Plugin {
    static CacheFiles cache = new CacheFiles("wmsplugin");

    WMSLayer wmsLayer;
    static JMenu wmsJMenu;

    static ArrayList<WMSInfo> wmsList = new ArrayList<WMSInfo>();
    static TreeMap<String,String> wmsListDefault = new TreeMap<String,String>();

    static boolean doOverlap = false;
    static int overlapEast = 14;
    static int overlapNorth = 4;
    static int simultaneousConnections = 3;
    // remember state of menu item to restore on changed preferences
    static private boolean menuEnabled = false;

    static boolean remoteControlAvailable = false;
    static String remoteControlVersion = null;

    protected void initExporterAndImporter() {
        ExtensionFileFilter.exporters.add(new WMSLayerExporter());
        ExtensionFileFilter.importers.add(new WMSLayerImporter());
    }

    public WMSPlugin(PluginInformation info) {
        super(info);
        /*
        System.out.println("constructor " + this.getClass().getName() + " (" + info.name +
                " v " + info.version + " stage " + info.stage + ")");
         */
        refreshMenu();
        cache.setExpire(CacheFiles.EXPIRE_MONTHLY, false);
        cache.setMaxSize(70, false);
        initExporterAndImporter();
        initRemoteControl();
    }

    /**
     * Check if remotecontrol plug-in is available and if its version is
     * high enough and add handler for "wms" remote control command "wms".
     */
    private void initRemoteControl() {
        final String remotecontrolName = "remotecontrol";
        final String remotecontrolMinVersion = "22675";
        for(PluginProxy pp: PluginHandler.pluginList)
        {
            PluginInformation info = pp.getPluginInformation();
            if(remotecontrolName.equals(info.name))
            {
                if(remotecontrolMinVersion.compareTo(info.version) <= 0)
                {
                    remoteControlAvailable = true;
                    remoteControlVersion = info.version;
                }
                else
                {
                    System.out.println("wmsplugin: remote control plugin version is " +
                            info.version + ", need " + remotecontrolMinVersion + " or newer");
                }
                break;
            }
        }

        if(remoteControlAvailable)
        {
            remoteControlAvailable = false;
            System.out.println("wmsplugin: initializing remote control");
            Plugin plugin =
                (Plugin) PluginHandler.getPlugin(remotecontrolName);
            try {
                Method method = plugin.getClass().getMethod("addRequestHandler", String.class, Class.class);
                method.invoke(plugin, WMSRemoteHandler.command, WMSRemoteHandler.class);
                remoteControlAvailable = true;
            } catch (SecurityException e) {
                e.printStackTrace();
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            }
        }
        if(!remoteControlAvailable)
        {
            System.out.println("wmsplugin: cannot use remote control");
        }
    }

    // this parses the preferences settings. preferences for the wms plugin have to
    // look like this:
    // wmsplugin.1.name=Landsat
    // wmsplugin.1.url=http://and.so.on/

    @Override
    public void copy(String from, String to) throws FileNotFoundException, IOException
    {
        File pluginDir = new File(getPrefsPath());
        if (!pluginDir.exists())
            pluginDir.mkdirs();
        FileOutputStream out = new FileOutputStream(getPrefsPath() + to);
        InputStream in = WMSPlugin.class.getResourceAsStream(from);
        byte[] buffer = new byte[8192];
        for(int len = in.read(buffer); len > 0; len = in.read(buffer))
            out.write(buffer, 0, len);
        in.close();
        out.close();
    }


    public static void refreshMenu() {
        wmsList.clear();
        Map<String,String> prefs = Main.pref.getAllPrefix("wmsplugin.url.");

        TreeSet<String> keys = new TreeSet<String>(prefs.keySet());

        // Here we load the settings for "overlap" checkbox and spinboxes.

        try {
            doOverlap = Boolean.valueOf(prefs.get("wmsplugin.url.overlap"));
        } catch (Exception e) {} // If sth fails, we drop to default settings.

        try {
            overlapEast = Integer.valueOf(prefs.get("wmsplugin.url.overlapEast"));
        } catch (Exception e) {} // If sth fails, we drop to default settings.

        try {
            overlapNorth = Integer.valueOf(prefs.get("wmsplugin.url.overlapNorth"));
        } catch (Exception e) {} // If sth fails, we drop to default settings.

        // Load the settings for number of simultaneous connections
        try {
            simultaneousConnections = Integer.valueOf(Main.pref.get("wmsplugin.simultanousConnections"));
        } catch (Exception e) {} // If sth fails, we drop to default settings.

        // And then the names+urls of WMS servers
        int prefid = 0;
        String name = null;
        String url = null;
        String cookies = "";
        int lastid = -1;
        for (String key : keys) {
            String[] elements = key.split("\\.");
            if (elements.length != 4) continue;
            try {
                prefid = Integer.parseInt(elements[2]);
            } catch(NumberFormatException e) {
                continue;
            }
            if (prefid != lastid) {
                name = url = null; lastid = prefid;
            }
            if (elements[3].equals("name"))
                name = prefs.get(key);
            else if (elements[3].equals("url"))
            {
                /* FIXME: Remove the if clause after some time */
                if(!prefs.get(key).startsWith("yahoo:")) /* legacy stuff */
                    url = prefs.get(key);
            }
            else if (elements[3].equals("cookies"))
                cookies = prefs.get(key);
            if (name != null && url != null)
                wmsList.add(new WMSInfo(name, url, cookies, prefid));
        }
        String source = "http://svn.openstreetmap.org/applications/editors/josm/plugins/wmsplugin/sources.cfg";
        try
        {
            MirroredInputStream s = new MirroredInputStream(source,
                    Main.pref.getPreferencesDir() + "plugins/wmsplugin/", -1);
            InputStreamReader r;
            try
            {
                r = new InputStreamReader(s, "UTF-8");
            }
            catch (UnsupportedEncodingException e)
            {
                r = new InputStreamReader(s);
            }
            BufferedReader reader = new BufferedReader(r);
            String line;
            while((line = reader.readLine()) != null)
            {
                String val[] = line.split(";");
                if(!line.startsWith("#") && val.length == 3)
                    setDefault("true".equals(val[0]), tr(val[1]), val[2]);
            }
        }
        catch (IOException e)
        {
        }

        Collections.sort(wmsList);
        MainMenu menu = Main.main.menu;

        if (wmsJMenu == null)
            wmsJMenu = menu.addMenu(marktr("WMS"), KeyEvent.VK_W, menu.defaultMenuPos, ht("/Plugin/WMS"));
        else
            wmsJMenu.removeAll();

        // for each configured WMSInfo, add a menu entry.
        for (final WMSInfo u : wmsList) {
            wmsJMenu.add(new JMenuItem(new WMSDownloadAction(u)));
        }
        wmsJMenu.addSeparator();
        wmsJMenu.add(new JMenuItem(new Map_Rectifier_WMSmenuAction()));

        wmsJMenu.addSeparator();
        wmsJMenu.add(new JMenuItem(new
                JosmAction(tr("Blank Layer"), "blankmenu", tr("Open a blank WMS layer to load data from a file"), null, false) {
            public void actionPerformed(ActionEvent ev) {
                Main.main.addLayer(new WMSLayer());
            }
        }));
        setEnabledAll(menuEnabled);
    }

    /* add a default entry in case the URL does not yet exist */
    private static void setDefault(Boolean force, String name, String url)
    {
        String testurl = url.replaceAll("=", "_");
        wmsListDefault.put(name, url);

        if(force && !Main.pref.getBoolean("wmsplugin.default."+testurl))
        {
            Main.pref.put("wmsplugin.default."+testurl, true);
            int id = -1;
            for(WMSInfo i : wmsList)
            {
                if(url.equals(i.url))
                    return;
                if(i.prefid > id)
                    id = i.prefid;
            }
            WMSInfo newinfo = new WMSInfo(name, url, id+1);
            newinfo.save();
            wmsList.add(newinfo);
        }
    }

    public static Grabber getGrabber(ProjectionBounds bounds, GeorefImage img, MapView mv, WMSLayer layer){
        if(layer.baseURL.startsWith("html:"))
            return new HTMLGrabber(bounds, img, mv, layer, cache);
        else
            return new WMSGrabber(bounds, img, mv, layer, cache);
    }

    private static void setEnabledAll(boolean isEnabled) {
        for(int i=0; i < wmsJMenu.getItemCount(); i++) {
            JMenuItem item = wmsJMenu.getItem(i);

            if(item != null) item.setEnabled(isEnabled);
        }
        menuEnabled = isEnabled;
    }

    @Override
    public void mapFrameInitialized(MapFrame oldFrame, MapFrame newFrame) {
        if (oldFrame==null && newFrame!=null) {
            setEnabledAll(true);
            Main.map.addMapMode(new IconToggleButton
                    (new WMSAdjustAction(Main.map)));
        } else if (oldFrame!=null && newFrame==null ) {
            setEnabledAll(false);
        }
    }

    @Override
    public PreferenceSetting getPreferenceSetting() {
        return new WMSPreferenceEditor();
    }

    static public String getPrefsPath()
    {
        return Main.pref.getPluginsDirectory().getPath() + "/wmsplugin/";
    }
}
