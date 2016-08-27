package Robotnikman;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Vector;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import JMegaHal.JMegaHAL;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.jsoup.Jsoup;



public class AICore extends JavaPlugin implements Listener{

	private AICore plugin = this;
	
	private File BrainFile;
	private File configf;
	private File learningmemory;	
	
	private Boolean LearnFromChat;
	
    private FileConfiguration config;   
    public static JMegaHAL hal = new JMegaHAL();
    
	private static Thread broadcaster;    
    
	public List<String> boards;
	public List<Integer> boarddepths;
	public static List<String> newSentences = new ArrayList<String>();
	public List<String> cachedSentences = new ArrayList<String>();
	public static Boolean debug = false;

	ai_API api;
    
    @Override
    public void onEnable(){
    	
    	getServer().getPluginManager().registerEvents(this, this);
    	
		boards = getConfig().getStringList("boards");
		boarddepths = getConfig().getIntegerList("boarddepths");
		debug = getConfig().getBoolean("debug");
		LearnFromChat = getConfig().getBoolean("LearnFromChat");
		wipebrain();
		
    	try {
			CreateOrLoadFiles();
		} catch (InvalidConfigurationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	
    	LoadIntoAI(hal, BrainFile);
    	
		try {
			loadmemory();
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}		
		
		if(boards.size() == 0) {
			boards.add("b");
			boarddepths.add(10);
		}
		
		try {
			for(int i = 0; i < boards.size(); i++){
			cache_sentences(boards.get(i),boarddepths.get(i));
			}
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		startAI();
		//set to update brain with new stuff from the internet
		broadcaster = new Thread(new BukkitRunnable() {
			final int hours = getConfig().getInt("update-frequency");
			@Override
			public void run() {
				Calendar c = Calendar.getInstance();
				c.set(Calendar.MINUTE, 0);
				c.set(Calendar.SECOND, 0);
				long nextExecution = c.getTimeInMillis() + hours*60*60*1000;
				while (true) {
					getLogger().info("Updating brain file with new content.");
					while (nextExecution > System.currentTimeMillis()) {
						try {
							Thread.sleep(nextExecution - System.currentTimeMillis());
						} catch (InterruptedException e) {}
					}
					if (broadcaster == null)
						return;
					wipebrain();
					//cache sentences
					try {
						for(int i = 0; i < boards.size(); i++){
							cache_sentences(boards.get(i),boarddepths.get(i));
							}
					} catch (ParseException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					//load memory file
					try {
						loadmemory();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					nextExecution += hours*60*60*1000;
				}
			}
		});
		broadcaster.start();
    }
    
	@Override
	public void onDisable(){
		this.writeNewSentences();
	}
    
    
    void CreateOrLoadFiles() throws InvalidConfigurationException {

        configf = new File(getDataFolder(), "config.yml");
        BrainFile = new File(this.getDataFolder(), "brain.AI");
        config = new YamlConfiguration();
        learningmemory = new File(this.getDataFolder(), "memory.AI");
        
        if (!configf.exists()) {
            configf.getParentFile().mkdirs();
            copy(getResource("config.yml"), configf);
        }
        
        if (!BrainFile.exists()){
            copy(getResource("brain.AI"), BrainFile);
        }
        
        if (!learningmemory.exists()){
            copy(getResource("memory.AI"), learningmemory);
        }        

        try {
            config.load(configf);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private void copy(InputStream in, File file) {
        try {
            OutputStream out = new FileOutputStream(file);
            byte[] buf = new byte[1024];
            int len;
            while((len=in.read(buf))>0){
                out.write(buf,0,len);
            }
            out.close();
            in.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public void debug(String message){
    	boolean debug = getConfig().getBoolean("debug");
    	if(debug == true){
    		getLogger().info(message);
    	}
    }
    
    public void LoadIntoAI(JMegaHAL AI ,File file){
		FileReader fr = null;
		try {
			fr = new FileReader(file);
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		BufferedReader br = new BufferedReader(fr);
		String line = null;
		try {
			while((line = br.readLine()) != null) {
				hal.add(line);
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		try {
			br.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }
    
	public String clean(String string) {
		if(string != null && string.length() > 300) {
			string = string.substring(0, 300);
		}
		String newstring = string.replaceAll("<.*?>", "").replaceAll("\\[.*?\\]", "");
		return newstring;
	}
    
    public void AddToBrain(String string){
    	hal.add(string);
    	this.newSentences.add(clean(string));
    }

    
    private void writeNewSentences() {
		try {
			FileWriter fw = new FileWriter(BrainFile, true);
			BufferedWriter bw = new BufferedWriter(fw);
			
			//get content from long term memory, which is not erased when brain is is wiped
			//brain.chester basically works as short term memory which is updated with new content
			FileWriter fw2 = new FileWriter(learningmemory, true);
			BufferedWriter bw2 = new BufferedWriter(fw2);
			
			for (String sentence : this.newSentences) {
				bw.write(sentence + "\n");
				bw2.write(sentence + "\n");
			}
			bw.close();
			bw2.close();
		} catch (IOException ioe) {
			ioe.printStackTrace();
		} finally {
		}

	}
	
	private void loadmemory() throws IOException{
		File learningmemory = new File(this.getDataFolder(), "memory.AI");
		File tempmemory = new File(this.getDataFolder(), "brain.AI");
		
		FileReader fr = new FileReader(learningmemory);
		BufferedReader br = new BufferedReader(fr);

		FileWriter fw = new FileWriter(tempmemory);
		BufferedWriter bw = new BufferedWriter(fw);
	
		String line = br.readLine();
		
		while(line != null){
			line = br.readLine();
			bw.write(line + "\n");
		}
		br.close();
		bw.close();
	}
	
	//wipes the brain file. Used to make sure the file does not grow too large
	//to-do: make it so file only gets wiped when it goes over a certain size
	private void wipebrain(){
		File chesterFile = new File(this.getDataFolder(), "brain.AI");
		
		try {
			PrintWriter writer = new PrintWriter(chesterFile);
			writer.print("");
			writer.close();
		} catch (IOException ioe) {
			ioe.printStackTrace();
		} finally {
		}
	}

	private void cache_sentences(String board, Integer boarddepth) throws ParseException {
		getLogger().info("caching new comments from board " + board + " to a depth of " + boarddepth.toString() + " pages");
		File chesterFile = new File(this.getDataFolder(), "brain.AI");
		try {
			FileWriter fw = new FileWriter(chesterFile, true);
			BufferedWriter bw = new BufferedWriter(fw);

			//get board URLs provided through parameter
			URL url = new URL("https://a.4cdn.org/" + board + "/threads.json");
			BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream()));
			String JSONstring = new String();
			String inputLine;
			JSONParser parser = new JSONParser();            
			while ((inputLine = in.readLine()) != null)
				JSONstring = inputLine; 
			in.close();


			JSONArray catalog=(JSONArray) parser.parse(JSONstring);


			Vector<URL> threadurls = new Vector<URL>();

			//Get thread numbers for the first 10 threads
			for(int j = 0; j<boarddepth; j++){
			JSONObject jsonObject = (JSONObject) parser.parse(catalog.get(j).toString());
			for(int i=0;i<10;i++){
				//String iteratorstring = Integer.toString(i);
				String threadnumber;
				String TURL;

				JSONArray threadObject = (JSONArray) parser.parse(jsonObject.get("threads").toString());
				JSONObject thread =  (JSONObject) parser.parse(threadObject.get(i).toString());				
				threadnumber = thread.get("no").toString();

				TURL = "https://a.4cdn.org/" + board + "/thread/" + threadnumber + ".json";

				threadurls.add(new URL(TURL));
			}
			}

			//Get threads from the urls got from previous loop
			for(int i = 0;i<threadurls.size(); i++ )
			{
				JSONstring = "";
				URL threadurl = threadurls.elementAt(i);
				in = new BufferedReader(new InputStreamReader(threadurl.openStream()));

				while ((inputLine = in.readLine()) != null)
					JSONstring = inputLine; 
				in.close();

				JSONObject thread =  (JSONObject) parser.parse(JSONstring);
				JSONArray posts =  (JSONArray) parser.parse(thread.get("posts").toString());

				//get comments from thread
				for(int j = 0; j < posts.size(); j++){
					JSONObject post = (JSONObject) parser.parse(posts.get(j).toString());
					if(post.get("com") != null){
						String sentence = striptags(post.get("com").toString());
						bw.write(sentence + "\n");
					}
				}

			}
			bw.close();			
		} catch (IOException ioe) {
			ioe.printStackTrace();
		}
	}

	private void startAI() {
		try {
			File AIFile = new File(this.getDataFolder(), "brain.AI");
			File dir = new File("plugins" + File.separator + "AI-Core");
			if(!dir.exists()) {
				dir.mkdirs();
			}

			if(AIFile.exists()) {
				FileReader fr = new FileReader(AIFile);
				BufferedReader br = new BufferedReader(fr);
				String line = null;
				while((line = br.readLine()) != null) {
					hal.add(line);
				}
				br.close();
			}
		} catch(IOException ioe) {
		}
		this.api = new ai_API(this, hal);
	}

	private JMegaHAL transfer(ObjectInputStream in) throws ClassNotFoundException, IOException {
		JMegaHAL hal = (JMegaHAL) in.readObject();
		if(in != null) {
			in.close();
		}
		return hal;
	}
	
	
	//Strips a string of HTML stuff and the comment things used on 4chna ex. >>49387923
	private String striptags(String sentence){
	
	String newsentence = Jsoup.parse(sentence).text();
	
	
	newsentence = newsentence.replaceAll(">>\\d{4,10}", "");

	return newsentence;
}

	static public void addSentence(String sentence) {
		newSentences.add(sentence);
	}
	
    static public String GenerateSentence(){
    	String sentence = GenerateSentence(null);
    	
		return sentence;
    }
    
    static public String GenerateSentence(String message){
    	String sentence = "BLANK";
    	if (message != null){
    		sentence = hal.getSentence(message);
    	}else{
    		sentence = hal.getSentence();
    	}
    	
		return sentence;
    }
    
    
    
//	@Override
//	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
//		String parameter = args[0];
//		if (label.equalsIgnoreCase("basecommand")) {
//			if (parameter.equalsIgnoreCase("reload")){
//				wipebrain();
//	
//				boards = getConfig().getStringList("boards");
//				boarddepths = getConfig().getIntegerList("boarddepths");
//				
//				try {
//					for(int i = 0; i < boards.size(); i++){
//						cache_sentences(boards.get(i),boarddepths.get(i));
//						}
//				} catch (ParseException e) {
//					// TODO Auto-generated catch block
//					e.printStackTrace();
//				}
//				
//				try {
//					loadmemory();
//				} catch (IOException e) {
//					// TODO Auto-generated catch block
//					e.printStackTrace();
//				}
//				
//				return true;
//			}
//			if (parameter.equalsIgnoreCase("generate")){
//				sender.sendMessage(GenerateSentence());
//				
//			}
//		}
//		return false;
//	}
	
	@EventHandler(ignoreCancelled = true)
	public void onChat(final AsyncPlayerChatEvent event) {
		if (LearnFromChat == true){
			debug("Player has chatted");
			final Player player = event.getPlayer();
			String message = event.getMessage();
			AddToBrain(clean(message));
		}
	}
	
    
}
