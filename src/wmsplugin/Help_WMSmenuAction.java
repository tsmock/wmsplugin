package wmsplugin;

import java.awt.event.ActionEvent;
import javax.swing.*;
import org.openstreetmap.josm.actions.JosmAction;



public class Help_WMSmenuAction extends JosmAction {

    /**
     * 
     */
    

    public Help_WMSmenuAction() {
        //super("Help / About");
        super("help", "help", "Help  / About", 0, 0, false);
        
    }
    
    public void actionPerformed(ActionEvent e) {
        //todo - put this into a txt file? 
          String helptext = 
            "You can add, edit and delete WMS entries in the WMSplugin Preference Tab - "  +
            "these will then show up in the WMS menu.\n\n"+
            
            "You can also do this manually in the Advanced Preferences, using the following schema:\n"+
            "wmsplugin.url.1.name=Landsat\n"+
            "wmsplugin.url.1.url=http://onearth.jpl.nasa.gov....\n"+
            "wmsplugin.url.2.name=NPE Maps... etc\n\n"+
                    
            "Full WMS URL input format example (landsat)\n"+
            "http://onearth.jpl.nasa.gov/wms.cgi?request=GetMap&\n"+
            "layers=global_mosaic&styles=&srs=EPSG:4326&\n"+
            "format=image/jpeg \n\n"+
            
            "For Metacartas Map Rectifier http://labs.metacarta.com/rectifier/ , you only need to input the relevant 'id'.\n" +
            "To add a Metacarta Map Rectifer menu item, manually create the url like in this example," +
            "replacing 73 with your image id: \n" +
            "http://labs.metacarta.com/rectifier/wms.cgi?id=73\n" +
            "&srs=EPSG:4326&Service=WMS&Version=1.1.0&Request=GetMap&format=image/png\n" +
            " \n" +
            "Note: Make sure the image is suitable, copyright-wise, if in doubt, don't use.\n"+
             "WMS Plugin version: "+ WMSPlugin.VERSION ;		
        
        JTextPane tp = new JTextPane();
          JScrollPane js = new JScrollPane(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                  JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
         
          
          js.getViewport().add(tp);
          JFrame jf = new JFrame("WMS Plugin Help");
          jf.getContentPane().add(js);
          jf.pack();
          jf.setSize(400,500);
          jf.setVisible(true); 
          tp.setText(helptext);
    
}
    
}
