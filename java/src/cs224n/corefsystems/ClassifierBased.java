package cs224n.corefsystems;

import cs224n.coref.*;
//import cs224n.corefsystems.RuleBased.ALL_ARTICLES;
import cs224n.corefsystems.HobbsAlgorithm;
import cs224n.util.Pair;
import edu.stanford.nlp.classify.LinearClassifier;
import edu.stanford.nlp.classify.LinearClassifierFactory;
import edu.stanford.nlp.classify.RVFDataset;
import edu.stanford.nlp.ling.RVFDatum;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.util.Triple;
import edu.stanford.nlp.util.logging.RedwoodConfiguration;
import edu.stanford.nlp.util.logging.StanfordRedwoodConfiguration;

import java.text.DecimalFormat;
import java.util.*;

import static edu.stanford.nlp.util.logging.Redwood.Util.*;

/**
 * @author Gabor Angeli (angeli at cs.stanford)
 */
public class ClassifierBased implements CoreferenceSystem {
  public static final String[] ALL_ARTICLES = new String[] {"a","an","the"};
          public static final Set<String> articles = new HashSet<String>(Arrays.asList(ALL_ARTICLES));

	private static <E> Set<E> mkSet(E[] array){
		Set<E> rtn = new HashSet<E>();
		Collections.addAll(rtn, array);
		return rtn;
	}

	private static final Set<Object> ACTIVE_FEATURES = mkSet(new Object[]{

			/*
			 * TODO: Create a set of active features
			 */

			Feature.ExactMatch.class,
			Feature.HW_Exact.class,
			Feature.HW_PoS.class,
			Feature.HW_NER.class,
			Feature.HW_Lemma.class,
			Feature.HW_Noun.class,
			Feature.HW_ProperNoun.class,
			Feature.HW_PluralNoun.class,
                        
                      
                      /*
                        
                        
                        
                        Feature.HW_WordInclusion.class,
                        Feature.HW_CompatibleModifiers.class,
                        
                        Feature.HW_SentenceDist.class,
                        Feature.HW_MentionDist.class,
                        
                        Feature.HW_NumberMatch.class,
                        Feature.HW_StrictNumberMatch.class,
                        Feature.HW_GenderMatch.class,
                        Feature.HW_StrictGenderMatch.class,
                        Feature.HW_PersonMatch.class,
                        Feature.HW_StrictPersonMatch.class,
                        
                        Feature.HW_Unigram.class,
                        Feature.HW_Bigram.class,
                        Feature.HW_OverlapCount.class,
                        Feature.HW_OnePronoun.class,
                        Feature.HW_BothPronoun.class,
                        Feature.HW_BothContainUppercase.class,
                        
                        
                      */

			//skeleton for how to create a pair feature
			//Pair.make(Feature.IsFeature1.class, Feature.IsFeature2.class),
	});


	private LinearClassifier<Boolean,Feature> classifier;

	public ClassifierBased(){
		StanfordRedwoodConfiguration.setup();
		RedwoodConfiguration.current().collapseApproximate().apply();
	}

	public FeatureExtractor<Pair<Mention,ClusteredMention>,Feature,Boolean> extractor = new FeatureExtractor<Pair<Mention, ClusteredMention>, Feature, Boolean>() {
		private <E> Feature feature(Class<E> clazz, Pair<Mention,ClusteredMention> input, Option<Double> count){
			
			//--Variables
			Mention onPrix = input.getFirst(); //the first mention (referred to as m_i in the handout)
			Mention candidate = input.getSecond().mention; //the second mention (referred to as m_j in the handout)
			Entity candidateCluster = input.getSecond().entity; //the cluster containing the second mention


			//--Features
			if(clazz.equals(Feature.ExactMatch.class)){
				//(exact string match)
				return new Feature.ExactMatch(onPrix.gloss().equals(candidate.gloss()));
			}
			else if(clazz.equals(Feature.HW_Exact.class)) {
			  // Head-word exact matching
	       return new Feature.HW_Exact(
	           onPrix.headWord().equals(candidate.headWord()));
			}
			else if(clazz.equals(Feature.HW_PoS.class)) {
			  // Head-word part of speech agreement
	      return new Feature.HW_PoS(
            onPrix.headToken().posTag().equals(candidate.headToken().posTag()));
			}
			else if(clazz.equals(Feature.HW_NER.class)) {
			  // Head-word NER agreement
	      return new Feature.HW_NER(
            onPrix.headToken().nerTag().equals(candidate.headToken().nerTag()));
			}
			else if(clazz.equals(Feature.HW_Lemma.class)) {
			  // Head-word lemma agreement
			  return new Feature.HW_Lemma(
            onPrix.headToken().lemma().equals(candidate.headToken().lemma()));
			}
			else if(clazz.equals(Feature.HW_Noun.class)) {
			  // Head-word isNoun agreement
			  return new Feature.HW_Noun(
            onPrix.headToken().isNoun() == candidate.headToken().isNoun());
			}
			else if(clazz.equals(Feature.HW_ProperNoun.class)) {
			  // Head-word isProperNoun agreement
			  return new Feature.HW_ProperNoun(
            onPrix.headToken().isProperNoun() == candidate.headToken().isProperNoun());
			}
			else if(clazz.equals(Feature.HW_PluralNoun.class)) {
			  // Head-word isPluralNoun agreement
			  return new Feature.HW_PluralNoun(
            onPrix.headToken().isPluralNoun() == candidate.headToken().isPluralNoun());
			}
                        else if(clazz.equals(Feature.HW_WordInclusion.class)) {
				
                                return new Feature.HW_WordInclusion(isWordInclusion(candidateCluster,onPrix));
			}
                       else if(clazz.equals(Feature.HW_CompatibleModifiers.class)) {
				
                                return new Feature.HW_CompatibleModifiers(isCompatibleModifiers( candidate,onPrix));
			}
                       else if(clazz.equals(Feature.HW_Unigram.class)) {
				
                                return new Feature.HW_Unigram(countOverlap( candidate.gloss(),onPrix.gloss())== 1);  
                       }
                      else if(clazz.equals(Feature.HW_Bigram.class)) {
				
                                return new Feature.HW_Bigram(countOverlap( candidate.gloss(),onPrix.gloss())== 2);
			}
                      else if(clazz.equals(Feature.HW_OverlapCount.class)) {
				
                                return new Feature.HW_OverlapCount(countOverlap(candidate.gloss(),onPrix.gloss()));
			}
                      
                      else if(clazz.equals(Feature.HW_OnePronoun.class)) {
				
                                return new Feature.HW_OnePronoun(Pronoun.isSomePronoun(candidate.gloss())||Pronoun.isSomePronoun(onPrix.gloss()));
			}
                     else if(clazz.equals(Feature.HW_BothPronoun.class)) {
				
                                return new Feature.HW_BothPronoun(Pronoun.isSomePronoun(candidate.gloss())&&Pronoun.isSomePronoun(onPrix.gloss()));
			}
                    else if(clazz.equals(Feature.HW_BothContainUppercase.class)) {
				
                                return new Feature.HW_BothContainUppercase(containUppercase(candidate.gloss())&&containUppercase(onPrix.gloss()));
			} 
                    else if(clazz.equals(Feature.HW_NumberMatch.class)) {
				
                                return new Feature.HW_NumberMatch(isNumberMatch(candidate ,onPrix));
			}
                    else if(clazz.equals(Feature.HW_StrictNumberMatch.class)) {
				
                                return new Feature.HW_StrictNumberMatch(isStrictNumberMatch(candidate ,onPrix));
			}
                    else if(clazz.equals(Feature.HW_GenderMatch.class)) {
				
                                return new Feature.HW_GenderMatch(isGenderMatch(candidate ,onPrix));
			}
                    else if(clazz.equals(Feature.HW_StrictGenderMatch.class)) {
				
                                return new Feature.HW_StrictGenderMatch(isStrictGenderMatch(candidate ,onPrix));
			}
                    else if(clazz.equals(Feature.HW_PersonMatch.class)) {
				
                                return new Feature.HW_PersonMatch(isPersonMatch(candidate ,onPrix));
			}
                    else if(clazz.equals(Feature.HW_StrictPersonMatch.class)) {
				
                                return new Feature.HW_StrictPersonMatch(isStrictPersonMatch(candidate ,onPrix));
			}
                    else if(clazz.equals(Feature.HW_SentenceDist.class)) {
				
                                return new Feature.HW_SentenceDist(onPrix.doc.indexOfSentence(onPrix.sentence) - candidate.doc.indexOfSentence(candidate.sentence));
			}
                    else if(clazz.equals(Feature.HW_MentionDist.class)) {
				
                                return new Feature.HW_MentionDist(onPrix.doc.indexOfMention(onPrix) - candidate.doc.indexOfMention(candidate));
			}
			else {
				throw new IllegalArgumentException("Unregistered feature: " + clazz);
			}
		}
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
                  * Compute the number of the words the two string overlap with each other
                  */
                public int countOverlap (String a, String b) {
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
                   return nCommon;
                }
                
                /**
                * compute the # of the words the two string overlap with each other
                */
                 public double countOverlapCount (String a, String b) {
                   int nWord  = 0;
                   int nCommon = 0;
                   String longStr;
                   String shortStr;
                   if (a.length() > b.length()){
                     longStr = a;
                     shortStr = b;
                   }
                   else{
                     longStr = b;
                     shortStr = a;
                   }

                   for (String word : longStr.split(" ")){
                     if (word!="the"){
                       nWord++;
                           if (shortStr.toLowerCase().contains(word.toLowerCase())){
                             nCommon++;
                             String firstLetter = String.valueOf(word.charAt(0)); 
                             /*if (firstLetter.equals(firstLetter.toUpperCase())){
                               nWord++;
                               nCommon++;
                             }*/
                           }
                       }    
                   }
                   double commonRatio =nCommon* 1.0/nWord;
                   return commonRatio;
                 }
                  /**
                  * Return true if a string contains a capitalized word
                  */
                
                  public boolean containUppercase(String a ) {

                     for (String word : a.split(" ")){

                      String firstLetter = String.valueOf(word.charAt(0)); 
                      if (firstLetter.equals(firstLetter.toUpperCase()))
                        return true;
                    }
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
                    * Returns TRUE if two mentions have strict gender match, otherwise FALSE
                    */
                   public boolean isStrictGenderMatch(Mention m1, Mention m2){
                     Pair<Boolean,Boolean> gender = Util.haveGenderAndAreSameGender(m1, m2);
                     return gender.getFirst() && gender.getSecond();
                   }

                   /**
                    * Returns TRUE if two mentions have number match, otherwise FALSE
                    */
                   public boolean isNumberMatch(Mention m1, Mention m2){
                     Pair<Boolean,Boolean> number = Util.haveNumberAndAreSameNumber(m1, m2);
                     return (number.getFirst() && number.getSecond()) || !number.getFirst();
                   }

                   /**
                    * Returns TRUE if two mentions have strict number match, otherwise FALSE
                    */
                   public boolean isStrictNumberMatch(Mention m1, Mention m2){
                     Pair<Boolean,Boolean> number = Util.haveNumberAndAreSameNumber(m1, m2);
                     return number.getFirst() && number.getSecond();
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
            /**
                    * Returns TRUE if two mentions have strict person match, otherwise FALSE
                    */
                   public boolean isStrictPersonMatch(Mention m1, Mention m2){
                     boolean flag = false;
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
  
		@SuppressWarnings({"unchecked"})
		@Override
		protected void fillFeatures(Pair<Mention, ClusteredMention> input, Counter<Feature> inFeatures, Boolean output, Counter<Feature> outFeatures) {
			//--Input Features
			for(Object o : ACTIVE_FEATURES){
				if(o instanceof Class){
					//(case: singleton feature)
					Option<Double> count = new Option<Double>(1.0);
					Feature feat = feature((Class) o, input, count);
					if(count.get() > 0.0){
						inFeatures.incrementCount(feat, count.get());
					}
				} else if(o instanceof Pair){
					//(case: pair of features)
					Pair<Class,Class> pair = (Pair<Class,Class>) o;
					Option<Double> countA = new Option<Double>(1.0);
					Option<Double> countB = new Option<Double>(1.0);
					Feature featA = feature(pair.getFirst(), input, countA);
					Feature featB = feature(pair.getSecond(), input, countB);
					if(countA.get() * countB.get() > 0.0){
						inFeatures.incrementCount(new Feature.PairFeature(featA, featB), countA.get() * countB.get());
					}
				}
			}

			//--Output Features
			if(output != null){
				outFeatures.incrementCount(new Feature.CoreferentIndicator(output), 1.0);
			}
		}

		@Override
		protected Feature concat(Feature a, Feature b) {
			return new Feature.PairFeature(a,b);
		}
	};

	public void train(Collection<Pair<Document, List<Entity>>> trainingData) {
		startTrack("Training");
		//--Variables
		RVFDataset<Boolean, Feature> dataset = new RVFDataset<Boolean, Feature>();
		LinearClassifierFactory<Boolean, Feature> fact = new LinearClassifierFactory<Boolean,Feature>();
		//--Feature Extraction
		startTrack("Feature Extraction");
		for(Pair<Document,List<Entity>> datum : trainingData){
			//(document variables)
			Document doc = datum.getFirst();
			List<Entity> goldClusters = datum.getSecond();
			List<Mention> mentions = doc.getMentions();
			Map<Mention,Entity> goldEntities = Entity.mentionToEntityMap(goldClusters);
			startTrack("Document " + doc.id);
			//(for each mention...)
			for(int i=0; i<mentions.size(); i++){
				//(get the mention and its cluster)
				Mention onPrix = mentions.get(i);
				Entity source = goldEntities.get(onPrix);
				if(source == null){ throw new IllegalArgumentException("Mention has no gold entity: " + onPrix); }
				//(for each previous mention...)
				int oldSize = dataset.size();
				for(int j=i-1; j>=0; j--){
					//(get previous mention and its cluster)
					Mention cand = mentions.get(j);
					Entity target = goldEntities.get(cand);
					if(target == null){ throw new IllegalArgumentException("Mention has no gold entity: " + cand); }
					//(extract features)
					Counter<Feature> feats = extractor.extractFeatures(Pair.make(onPrix, cand.markCoreferent(target)));
					//(add datum)
					dataset.add(new RVFDatum<Boolean, Feature>(feats, target == source));
					//(stop if
					if(target == source){ break; }
				}
				//logf("Mention %s (%d datums)", onPrix.toString(), dataset.size() - oldSize);
			}
			endTrack("Document " + doc.id);
		}
		endTrack("Feature Extraction");
		//--Train Classifier
		startTrack("Minimizer");
		this.classifier = fact.trainClassifier(dataset);
		endTrack("Minimizer");
		//--Dump Weights
		startTrack("Features");
		//(get labels to print)
		Set<Boolean> labels = new HashSet<Boolean>();
		labels.add(true);
		//(print features)
		for(Triple<Feature,Boolean,Double> featureInfo : this.classifier.getTopFeatures(labels, 0.0, true, 100, true)){
			Feature feature = featureInfo.first();
			Boolean label = featureInfo.second();
			Double magnitude = featureInfo.third();
			//log(FORCE,new DecimalFormat("0.000").format(magnitude) + " [" + label + "] " + feature);
		}
		end_Track("Features");
		endTrack("Training");
	}

	public List<ClusteredMention> runCoreference(Document doc) {
		//--Overhead
		startTrack("Testing " + doc.id);
		//(variables)
		List<ClusteredMention> rtn = new ArrayList<ClusteredMention>(doc.getMentions().size());
		List<Mention> mentions = doc.getMentions();
		int singletons = 0;
		//--Run Classifier
		for(int i=0; i<mentions.size(); i++){
			//(variables)
			Mention onPrix = mentions.get(i);
			int coreferentWith = -1;
			//(get mention it is coreferent with)
			for(int j=i-1; j>=0; j--){

				ClusteredMention cand = rtn.get(j);
				
				boolean coreferent = classifier.classOf(new RVFDatum<Boolean, Feature>(
						       extractor.extractFeatures(Pair.make(onPrix, cand))));
				
				if(coreferent){
					coreferentWith = j;
					break;
				}
			}

			if(coreferentWith < 0){
				singletons += 1;
				rtn.add(onPrix.markSingleton());
			} else {
				//log("Mention " + onPrix + " coreferent with " + mentions.get(coreferentWith));
				rtn.add(onPrix.markCoreferent(rtn.get(coreferentWith)));
			}
		}
		//log("" + singletons + " singletons");
		//--Return
		endTrack("Testing " + doc.id);
		//RuleBased.pronounMatch(rtn); /*****/ rtn = RuleBased.updateCMList(rtn);
		return rtn;
	}

	private class Option<T> {
		private T obj;
		public Option(T obj){ this.obj = obj; }
		public Option(){};
		public T get(){ return obj; }
		public void set(T obj){ this.obj = obj; }
		public boolean exists(){ return obj != null; }
	}
	
	
  public static void pronounMatch(List<ClusteredMention> currentClusters) {
    // Separate pronouns from non-pronouns
    List<ClusteredMention> proList = new ArrayList<ClusteredMention>();
    List<ClusteredMention> nonList = new ArrayList<ClusteredMention>();
    for (ClusteredMention cm : currentClusters) {
      if (Pronoun.isSomePronoun(cm.mention.headWord())) {
        proList.add(cm);
      } else {
        nonList.add(cm);
      }
    }
    
    // Assign every pronoun to the best match
    for (ClusteredMention pro : proList) {
      Entity bestMatch = null;
      int bestDistance = Integer.MAX_VALUE;
      
      for (ClusteredMention non : nonList) {
        int distance = pro.mention.doc.indexOfMention(pro.mention) - non.mention.doc.indexOfMention(non.mention);
        if (distance < bestDistance && distance > 0
            && isNerMatch(pro.mention, non.mention)
            && isGenderMatch(pro.mention, non.mention)
            && isNumberMatch(pro.mention, non.mention)
            //&& isPersonMatch(pro.mention, non.mention)
            ) {
          bestDistance = distance;
          bestMatch = non.entity;
        }
      }
      if (bestMatch != null)
        mergeClusters(pro.entity, bestMatch);
    }
  }
  /**
   * Returns TRUE if two mentions are labeled as the named entities and the types match, otherwise FALSE
   */
  public static boolean isNerMatch(Mention mention, Mention newMention){
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
  public static boolean isGenderMatch(Mention m1, Mention m2){
    Pair<Boolean,Boolean> gender = Util.haveGenderAndAreSameGender(m1, m2);
    return(gender.getFirst() && gender.getSecond())|| !gender.getFirst();
  }
  
  /**
   * Returns TRUE if two mentions have number match, otherwise FALSE
   */
  public static boolean isNumberMatch(Mention m1, Mention m2){
    Pair<Boolean,Boolean> number = Util.haveNumberAndAreSameNumber(m1, m2);
    return (number.getFirst() && number.getSecond()) || !number.getFirst();
  }
  /**
   * Merge all mentions from e1 to e2
   */
  public static void mergeClusters(Entity e1, Entity e2) {
    List<Mention> m1List = new ArrayList<Mention>();
    for (Mention m1 : e1.mentions) {
      m1List.add(m1);
    }
    for (Mention m1 : m1List) {
      m1.changeCoreference(e2);
    }
  }
	
}
