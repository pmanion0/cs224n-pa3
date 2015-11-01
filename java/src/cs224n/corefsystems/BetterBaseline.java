package cs224n.corefsystems;

import java.util.Collection;
import java.util.List;
import java.util.ArrayList;
 

import cs224n.coref.ClusteredMention;
import cs224n.coref.Document;
import cs224n.coref.*;
import cs224n.util.Pair;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class BetterBaseline implements CoreferenceSystem {

  @Override
  public void train(Collection<Pair<Document, List<Entity>>> trainingData) {
    for(Pair<Document, List<Entity>> pair : trainingData){
      Document doc = pair.getFirst();
      List<Entity> clusters = pair.getSecond();
      List<Mention> mentions = doc.getMentions();
       //--Iterate over mentions
       for(Mention m : mentions){
       }
       //--Iterate Over Coreferent Mention Pairs
       for(Entity e : clusters){
         for(Pair<Mention, Mention> mentionPair : e.orderedMentionPairs()){
         //System.out.println(""+mentionPair.getFirst() + " and " + mentionPair.getSecond() );
         }
       }
    }  
  }

  @Override
  public List<ClusteredMention> runCoreference(Document doc) {
    List<ClusteredMention> mentions = new ArrayList<ClusteredMention>();
    Map<String,Entity> clusters = new HashMap<String,Entity>();
    
    for(Mention m : doc.getMentions()){
      String mentionString = m.gloss().toLowerCase();
      String similarWord = partialMatch(clusters.keySet(), mentionString); 
       if(similarWord!=null | clusters.containsKey(mentionString)){
         if (clusters.containsKey(mentionString))
           mentions.add(m.markCoreferent(clusters.get(mentionString)));
         else
           mentions.add(m.markCoreferent(clusters.get(similarWord)));
       }
       else{
         ClusteredMention newCluster = m.markSingleton();
         mentions.add(newCluster);
         clusters.put(mentionString,newCluster.entity);
       }
    }  
    return mentions;
  }

//use the overlap percent to decide if a stringset contains a specific string
  public String partialMatch(Set<String> stringSet, String a){
    if (stringSet!=null){
      for (String string : stringSet){
        if (countOverlapPercent(string, a) > 0.67)
          return string.toLowerCase();
      }
    }
    return null;
  }

// compute the overlap percent of the two strings
  public double countOverlapPercent (String a, String b) {
    int nWord  = 0;
    int nCommon = 0;
    String longStr;
    String shortStr;
    if (a.length() > b.length()){
      longStr = a;
      shortStr =b;
    }
    else{
      longStr = b;
      shortStr =a;
    }

    for (String word : longStr.split(" ")){
      if (word!="the"){
        nWord++;
        if (shortStr.toLowerCase().contains(word.toLowerCase()))
          nCommon++;
        }    
    }
    double commonRatio =nCommon* 1.0/nWord;
    return commonRatio;
  }
  
  //check if two string contain exactly the same words though probably different order
  public boolean containsUnorderred(String a, String b) {
    boolean flag = true;
    for (String word : a.split(" ")){
      if (!b.toLowerCase().contains(word.toLowerCase()))
        flag = false;
    }    
    return flag;
  }

}

  



    