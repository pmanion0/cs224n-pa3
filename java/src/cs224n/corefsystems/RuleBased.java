package cs224n.corefsystems;

import java.util.Collection;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

import cs224n.coref.*;
import cs224n.ling.Tree;
import cs224n.util.CounterMap;
import cs224n.util.Pair;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

public class RuleBased implements CoreferenceSystem {
  
  public CounterMap<String,String> synonyms = new CounterMap<String,String>();
  
  // Create word sets that we will treat differently and/or exclude from certain rules
  public static final String[] ALL_PRONOUNS = new String[] {"i","you","he","she","it","me","us","we","them",
      "him","her","his","hers","my","yours","ours","our"};
  public static final String[] ALL_ARTICLES = new String[] {"a","an","the"};
  public static final String[] ALL_PARSE_NOUNS = new String[] {"NN","NNS","NNP","NNPS"};
  
  public static final Set<String> pronouns = new HashSet<String>(Arrays.asList(ALL_PRONOUNS));
  public static final Set<String> articles = new HashSet<String>(Arrays.asList(ALL_ARTICLES));
  public static final Set<String> parseNouns = new HashSet<String>(Arrays.asList(ALL_PARSE_NOUNS));


  @Override
  public void train(Collection<Pair<Document, List<Entity>>> trainingData) {
    /*for(Pair<Document, List<Entity>> pair : trainingData){
      List<Entity> clusters = pair.getSecond();
      
      trainSynonyms(clusters);
    }*/
  }

  @Override
  public List<ClusteredMention> runCoreference(Document doc) {
    List<ClusteredMention> output;
    
    // Phase 1: Exact Matches
    output = exactMatch(null, doc);
    
    // Phase 2: Strict Head Matches
    output = strictHeadMatch(output, doc);
            
    // Phase 3: Strict Head Matches - Var1
    output = strictHeadMatchVar1(output, doc);
    
    // Phase 4: Strict Head Matches - Var2
    output = strictHeadMatchVar2(output, doc);
    
    // Phase 5: Relaxed Head Matches
    output = relaxedHeadMatch(output, doc);
    
    // Phase 6: Pronoun Matches
    output = pronounMatch(output, doc);
    
    // Phase 7: Hobbs Algorithm
    //output = hobbsMatch(output, doc);
    
    return output;
  }
  
  /**
   * Run the Hobbs Algorithm
   */
  public List<ClusteredMention> hobbsMatch(List<ClusteredMention> currentClusters, Document doc) {
    /* DEBUG */ int pronoun = 0, nohobbsmatch = 0, nomention = 0;
    List<ClusteredMention> output = new ArrayList<ClusteredMention>();
    
    for (ClusteredMention cm : currentClusters) {
      // Skip non-pronouns and already clustered mentions
      if (!Pronoun.isSomePronoun(cm.mention.gloss())) {// || cm.entity != null) {
        output.add(cm);
        continue;
      }
      /* DEBUG */ pronoun++;
      // Proceed with Hobbs Algorithm otherwise
      int sentenceIndex = doc.indexOfSentence(cm.mention.sentence);
      Pair<Integer,Integer> hobbs = HobbsAlgorithm.parse(doc, sentenceIndex, cm.mention);
      if (hobbs.getFirst() == -1 || hobbs.getSecond() == -1) {
        output.add(cm);
        /* DEBUG */ nohobbsmatch++;
        continue;
      }
      
      // Convert the Hobbs output into a mention
      int hobbsUID = hobbs.getFirst();
      Sentence hobbsSentence = doc.sentences.get(hobbs.getSecond());
      Tree<String> hobbsParse = HobbsAlgorithm.returnSubtree(hobbsSentence.parse, hobbsUID);
      List<String> hobbsPhrase = hobbsParse.getYield();
      ClusteredMention hoobsMention = hobbsToMention(currentClusters, hobbsSentence, hobbsPhrase);
      
      if (hoobsMention != null) {
        output.add(cm.mention.changeCoreference(hoobsMention.entity));
      } else {
        /* DEBUG */ nomention++;
        output.add(cm);
      }
    }
    System.err.println("PRONOUNS: " + pronoun + "  NOHOBBSMATCH: " + nohobbsmatch + "  NOMENTION: " + nomention);
    return output;
  }
  /**
   * Convert Hobbs output to a mention
   */
  public static ClusteredMention hobbsToMention(List<ClusteredMention> clusters, Sentence s, List<String> words) {
    for (ClusteredMention cm : clusters) {
      Mention m = cm.mention;
      if (m.sentence == s && stringListMatch(m.text(), words))
        return cm;
    }
    return null;
  }
  /**
   * Determine if the two lists of strings match
   */
  public static boolean stringListMatch(List<String> a, List<String> b) {
    if (a.size() != b.size())
      return false;
    for (int i=0; i < a.size(); i++) {
      if (!a.get(i).equals(b.get(i))) {
        return false;
      }
    }
    return true;
  }
  
  
  /**
   * Cluster all mentions that are exact string matches
   */
  public List<ClusteredMention> exactMatch(List<ClusteredMention> currentClusters, Document doc) {
    Map<String,Entity> clusters = new HashMap<String,Entity>();
    List<ClusteredMention> output = new ArrayList<ClusteredMention>();
    
    for (Mention m : doc.getMentions()) {
      String mentionString = m.gloss().toLowerCase();
      if (clusters.containsKey(mentionString)) {
        output.add(m.markCoreferent(clusters.get(mentionString)));
      } else {
        ClusteredMention newCluster = m.markSingleton();
        output.add(newCluster);
        clusters.put(mentionString,newCluster.entity);
      }
    }
    return output;
  }
  
  
  
 /**
   * phase 3. Combine clusters with strict head matching
   */
  public List<ClusteredMention> strictHeadMatch(List<ClusteredMention> currentClusters, Document doc) {
    List<ClusteredMention> output = new ArrayList<ClusteredMention>();
    
    for (ClusteredMention curr : currentClusters) {
      Mention mention = curr.mention;
      Entity bestMatch = getStrictHeadMatch(currentClusters, mention);
      
      if (bestMatch != null) {
        curr.mention.changeCoreference(bestMatch);
      }
      output.add(curr);
    }
    return output;
  }


  /**
   * phase 3. - check if a mention satisfies strict head matching with an current cluster
   */
   
  public Entity getStrictHeadMatch(List<ClusteredMention> clusters, Mention newMention) {
    Entity bestEntity = null;
     
    if (clusters != null) {
      for (ClusteredMention cm : clusters) {
        Mention mention = cm.mention;
        Entity  entity = cm.entity;
        if (isClusterHeadMatch(entity, newMention) && isWordInclusion(entity, newMention) && isCompatibleModifiers(mention, newMention))
        bestEntity = entity;    
      }
    }
    return bestEntity;
  } 
  
  /**
   * phase 4. Combine clusters with strict head matching - variant I 
   */
  public List<ClusteredMention> strictHeadMatchVar1(List<ClusteredMention> currentClusters, Document doc) {
    List<ClusteredMention> output = new ArrayList<ClusteredMention>();
    
    for (ClusteredMention curr : currentClusters) {
      Mention mention = curr.mention;
      Entity bestMatch = getStrictHeadMatchVar1(currentClusters, mention);
      
      if (bestMatch != null) {
        curr.mention.changeCoreference(bestMatch);
      }
      output.add(curr);
    }
    return output;
  }


  /**
   * phase 4. - check if a mention satisfies strict head matching - variant I with an current cluster
   */
   
  public Entity getStrictHeadMatchVar1(List<ClusteredMention> clusters, Mention newMention) {
    Entity bestEntity = null;
     
    if (clusters != null) {
      for (ClusteredMention cm : clusters) {
        Mention mention = cm.mention;
        Entity  entity = cm.entity;
        if (isClusterHeadMatch(entity, newMention) && isWordInclusion(entity, newMention))
        bestEntity = entity;    
      }
    }
    return bestEntity;
  } 
  
  /**
   * phase 5. Combine clusters with strict head matching -- Var2
   */
  public List<ClusteredMention> strictHeadMatchVar2(List<ClusteredMention> currentClusters, Document doc) {
    List<ClusteredMention> output = new ArrayList<ClusteredMention>();
    for (ClusteredMention curr : currentClusters) {
      Mention mention = curr.mention;
      Entity bestMatch = getStrictHeadMatchVar2(currentClusters, mention);
      
      if (bestMatch != null) {
        curr.mention.changeCoreference(bestMatch);
      }
      output.add(curr);
    }
    return output;
  }


  /**
   * phase 5. - check if a mention satisfies strict head matching variant 2 with an current cluster
   */
   
  public Entity getStrictHeadMatchVar2(List<ClusteredMention> clusters, Mention newMention) {
    Entity bestEntity = null;
     
    if (clusters != null) {
      for (ClusteredMention cm : clusters) {
        Mention mention = cm.mention;
        Entity  entity = cm.entity;
        if (isClusterHeadMatch(entity, newMention)  && isCompatibleModifiers(mention, newMention))
        bestEntity = entity;    
      }
    }
    return bestEntity;
  } 
  
/*/**
   * phase 6. Combine clusters with relax head match
   */
  public List<ClusteredMention> relaxedHeadMatch(List<ClusteredMention> currentClusters, Document doc) {
    List<ClusteredMention> output = new ArrayList<ClusteredMention>();
    
    for (ClusteredMention curr : currentClusters) {
      Mention mention = curr.mention;
      Entity bestMatch = getRelaxedHeadMatch(currentClusters, mention);
      
      if (bestMatch != null) {
        curr.mention.changeCoreference(bestMatch);
      }
      output.add(curr);
    }
    return output;
  }

  /**
   * phase 6. - check if a mention satisfies strict head matching variant 2 with an current cluster
   */
   
  public Entity getRelaxedHeadMatch(List<ClusteredMention> clusters, Mention newMention) {
    Entity bestEntity = null;
     
    if (clusters != null) {
      for (ClusteredMention cm : clusters) {
        Mention mention = cm.mention;
        Entity  entity = cm.entity;
        if (isRelaxedClusterHeadMatch(entity, newMention)  && isNerMatch(mention, newMention) && isWordInclusion(entity, newMention))
        bestEntity = entity;    
      }
    }
    return bestEntity;
  } 
  
  /**
   * phase 7. Combine clusters with Pronoun match
   */
  public List<ClusteredMention> pronounMatch(List<ClusteredMention> currentClusters, Document doc) {
    List<ClusteredMention> output = new ArrayList<ClusteredMention>();
    
    for (ClusteredMention curr : currentClusters) {
      Mention mention = curr.mention;
      Entity bestMatch = getPronounMatch(currentClusters, mention);
      
      if (bestMatch != null) {
        curr.mention.changeCoreference(bestMatch);
      }
      output.add(curr);
    }
    return output;
  }

  

  /**
   * phase 6. - check if a mention satisfies strict head matching variant 2 with an current cluster
   */
   
  public Entity getPronounMatch(List<ClusteredMention> clusters, Mention newMention) {
    Entity bestEntity = null;
     
    if (clusters != null) {
      for (ClusteredMention cm : clusters) {
        Mention mention = cm.mention;
        Entity  entity = cm.entity;
        if (isPronounMatch(mention, newMention))
        bestEntity = entity;    
      }
    }
    return bestEntity;
  } 
  
  /**
   * Returns TRUE if a newMention satisfy Cluster Head Match with an entity, otherwise FALSE
   */
  public boolean isClusterHeadMatch(Entity entity, Mention newMention){
    for (Mention m : entity.mentions){
      if (m.headWord().equals(newMention.headWord()))
              return true;
    }
    return false;
  }
  
  
  /**
   * Returns TRUE if Word Inclusion is satisfied, otherwise FALSE, not finished - need to remove stop words
   */
  public boolean isWordInclusion(Entity entity, Mention newMention){
    String clusterString = new String();
      for (Mention m : entity.mentions)
        clusterString += " " + m.gloss().toLowerCase();
      
    for (String word : newMention.gloss().toLowerCase().split(" ")){
      if (!isArticle(word) && !clusterString.contains(word))
        return false;
    }
    return true;
  }
  
  /**
   * Returns TRUE if two mentions have Compatible Modifiers is satisfied, otherwise FALSE
   */
  public boolean isCompatibleModifiers(Mention mention, Mention newMention){
    String newText = newMention.gloss();
    List<String> newPosTags = newMention.sentence.posTags;
    int index = newMention.beginIndexInclusive;
    String posTag;
    for (String word : newText.split(" ")){
      posTag = newPosTags.get(index);
      if (posTag.equals("NN") || posTag.equals("JJ")|| posTag.equals("JJS") || posTag.equals("JJR")){
        if (!mention.gloss().contains(word))
          return false;
      index++;
      }
    }
    return false;
  }
  
  /**
   * Returns TRUE if a newMention satisfy Cluster Head Match with an entity, otherwise FALSE
   */
  public boolean isRelaxedClusterHeadMatch(Entity entity, Mention newMention){
    for (Mention m : entity.mentions){
      if (m.gloss().toLowerCase().contains(newMention.headWord().toLowerCase()))
              return true;
    }
    return false;
  }
  
  /**
   * Returns TRUE if two mentions are labeled as the named entities and the types match, otherwise FALSE
   */
  public boolean isNerMatch(Mention mention, Mention newMention){
    String nerTag = mention.sentence.nerTags.get(mention.beginIndexInclusive);
    String newNerTag = newMention.sentence.nerTags.get(newMention.beginIndexInclusive);
    if (nerTag!= "0" && nerTag.equals(newNerTag))
      return true;
    else
      return false;
  }
  
   /**
   * Returns TRUE if two mentions have gender match, otherwise FALSE
   */
  public boolean isGenderMatch(Mention m1, Mention m2){
    Pair<Boolean,Boolean> gender = Util.haveGenderAndAreSameGender(m1, m2);
    return(gender.getFirst() && gender.getSecond())|| !gender.getFirst();
  }
  
  /**
   * Returns TRUE if two mentions have number match, otherwise FALSE
   */
  public boolean isNumberMatch(Mention m1, Mention m2){
    Pair<Boolean,Boolean> number = Util.haveNumberAndAreSameNumber(m1, m2);
    return (number.getFirst() && number.getSecond()) || !number.getFirst();
  }
  
  /**
   * Returns TRUE if two mentions have person match, otherwise FALSE
   */
  public boolean isPersonMatch(Mention m1, Mention m2){
    boolean flag = true;
    if (Pronoun.isSomePronoun(m1.gloss()) && Pronoun.isSomePronoun(m2.gloss())) {
      if (!(m1.headToken().isQuoted() || m2.headToken().isQuoted())) {
        Pronoun p1 = Pronoun.valueOrNull(m1.gloss());
        Pronoun p2 = Pronoun.valueOrNull(m2.gloss());
        if (p1 != null && p2 != null) {
          flag = (p1.speaker == p2.speaker);
        }
      }
    }
    return flag;
  }
  
  boolean isPronounMatch(Mention m1, Mention m2) {
     
    boolean nerMatch = m1.headToken().nerTag().equals(m2.headToken().nerTag());
    boolean lemmasMatch = m1.headToken().lemma().equals(m2.headToken().lemma());

    return isGenderMatch(m1,m2) && isNumberMatch(m1,m2) && isPersonMatch(m1,m2) && nerMatch && lemmasMatch;
  }
   /**
   * Combine clusters with some percent overlap in 
   */
  public List<ClusteredMention> partialMatch(List<ClusteredMention> currentClusters, Document doc) {
    List<ClusteredMention> output = new ArrayList<ClusteredMention>();
    
    for (ClusteredMention curr : currentClusters) {
      String text = curr.mention.gloss();
      Entity bestMatch = getBestMatch(currentClusters, text);
      
      if (bestMatch != null) {
        curr.mention.changeCoreference(bestMatch);
      }
      output.add(curr);
    }
    return output;
  }
  
  
  /**
   * Use the overlap percent to decide if a String Set contains a specific string
   */
  private static final double MATCH_THRESHOLD = 0.66;
  
  public Entity getBestMatch(List<ClusteredMention> clusters, String newText) {
    Entity bestEntity = null;
    double bestOverlap = 0.0;
    
    if (clusters != null) {
      for (ClusteredMention cm : clusters) {
        String clusterText = cm.mention.gloss();
        double overlap = countOverlapPercent(clusterText, newText);
        if (overlap > MATCH_THRESHOLD && overlap > bestOverlap) {
          bestOverlap = overlap;
          bestEntity = cm.entity;
        }
      }
    }
    return bestEntity;
  }
  
  
  /**
   * Compute the percent of the words the two string overlap with each other
   */
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
      if (!isArticle(word)){
        nWord++;
        if (shortStr.toLowerCase().contains(word.toLowerCase()))
          nCommon++;
        }    
    }
    double commonRatio =nCommon* 1.0/nWord;
    return commonRatio;
  }
  
  
  /**
   * Updates CounterMap of co-occurences in the mention words
   */
  public void trainSynonyms(List<Entity> clusters) {
    for (Entity e : clusters) {
      // Put all mention words into a List
      List<String> mentionWords = new ArrayList<String>();
      
      for (Mention mention : e.mentions) {
        for (String word : mention.gloss().split(" ")) {
          if (!isPronoun(word) && !isArticle(word)) {
            mentionWords.add(word);
          }
        }
      }
      
      // Store count of all co-occurences 
      for (int i=0; i < mentionWords.size(); i++) {
        String iWord = mentionWords.get(i);
        for (int j=0; j < mentionWords.size(); j++) {
          if (i != j) {
            String jWord = mentionWords.get(j);
            synonyms.incrementCount(iWord, jWord, 1);
          }
        }
      }
    }
  }
  
  
  /** 
   * check if two string contain exactly the same set of words which may not be in the same order
   */
  public boolean containsUnorderredWords(String a, String b) {
    boolean flag = true;
    for (String word : a.split(" ")){
      if (!b.toLowerCase().contains(word.toLowerCase()))
        flag = false;
    }    
    return flag;
  }
  
  
  /**
   * Returns TRUE if the word is a pronoun, otherwise FALSE
   */
  public boolean parseIsNoun(String tag) {
    if (parseNouns.contains(tag.toLowerCase())) {
      return true;
    } else {
      return false;
    }
  }
  
  
  /**
   * Returns TRUE if the word is a pronoun, otherwise FALSE
   */
  public boolean isPronoun(String word) {
    if (pronouns.contains(word.toLowerCase())) {
      return true;
    } else {
      return false;
    }
  }
  
  
  /**
   * Returns TRUE if the word is an article, otherwise FALSE
   */
  public boolean isArticle(String word) {
    if (articles.contains(word.toLowerCase())) {
      return true;
    } else {
      return false;
    }
  }
  
  
  /**
   * Get all entities present in the ClusteredMention list
   */
  public Set<Entity> getEntities(List<ClusteredMention> clusters) {
    Set<Entity> entities = new HashSet<Entity>();
    for (ClusteredMention cm: clusters) {
      entities.add(cm.entity);
    }
    return entities;
  }
}