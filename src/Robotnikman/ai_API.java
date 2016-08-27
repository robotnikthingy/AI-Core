package Robotnikman;

import java.io.File;

import org.bukkit.plugin.Plugin;
import JMegaHal.JMegaHAL;

public class ai_API {

	AICore plugin;
	private JMegaHAL hal;


	
	
	public ai_API(AICore plugin2, JMegaHAL hal) {
		this.plugin = plugin2;
		this.hal = hal;


	}


	public void addSentence(String sentence) {
		plugin.newSentences.add(sentence);
	}
	
    public String GetSentence(){

    	String sentence = GetSentence(null);
		return sentence;
    }
    
    public String GetSentence(String message){
    	String sentence = "BLANK";
    	if (message != null){
        	plugin.getLogger().info("Retrieving sentence");
    		sentence = hal.getSentence(message);
    	}else{
        	plugin.getLogger().info("Retrieving sentence, no string provided");
    		sentence = hal.getSentence();
    	}
    	plugin.getLogger().info("sentence is: " + sentence);
		return sentence;
    }
}
