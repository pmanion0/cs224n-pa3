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
		// TODO Auto-generated method stub
            for(Pair<Document, List<Entity>> pair : trainingData){
      //--Get Variables
                Document doc = pair.getFirst();
                List<Entity> clusters = pair.getSecond();
                List<Mention> mentions = doc.getMentions();
                //--Print the Document
          //      System.out.println(doc.prettyPrint(clusters));
                //--Iterate over mentions
                for(Mention m : mentions){
          //        System.out.println(m);
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
            //(for each mention...)
            for(Mention m : doc.getMentions()){
              //(...get its text)
               String mentionString = m.gloss().toLowerCase();
               String similarWord = partialMatch(clusters.keySet(), mentionString); 
              //(...if we've seen this text before...)
              if(similarWord!=null | clusters.containsKey(mentionString)){
                //(...add it to the cluster)
                  if (clusters.containsKey(mentionString))
                    mentions.add(m.markCoreferent(clusters.get(mentionString)));
                  else
                     mentions.add(m.markCoreferent(clusters.get(similarWord)));
              } else {
                //(...else create a new singleton cluster)
                ClusteredMention newCluster = m.markSingleton();
                mentions.add(newCluster);
                clusters.put(mentionString,newCluster.entity);
              }

           //explore mention,sentence functions
          /*
            if (m.gloss().equals("God the Protector")) {
                    System.out.println(m.sentence.parse);
                    System.out.println(m.parse);
                    System.out.println(m.beginIndexInclusive + " "+m.endIndexExclusive);
                    System.out.println(m.gloss());
                    System.out.println(m.sentence.posTags);
                    System.out.println(m.sentence.nerTags);
                    System.out.println(m.sentence.lemmas);
                    System.out.println(m.sentence.tokens);
                    System.out.println(m.sentence.speakersOfWord);
                    String a ="String";
                    System.out.println(a.toLowerCase());
              }
           */
     
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
               
            


}

  



    