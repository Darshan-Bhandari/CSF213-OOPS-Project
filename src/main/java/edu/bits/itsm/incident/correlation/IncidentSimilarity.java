package edu.bits.itsm.incident.correlation;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Small deterministic vocabulary for common campus incident wording. */
final class IncidentSimilarity {
  private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}]+");
  private static final Set<String> COMMON = Set.of(
      "about", "after", "again", "also", "and", "are", "been", "but", "can", "for",
      "from", "had", "has", "have", "here", "how", "into", "its", "just", "my", "not",
      "now", "our", "please", "some", "that", "the", "their", "there", "they", "this",
      "too", "was", "were", "what", "when", "where", "which", "will", "with", "you",
      "your", "campus", "issue", "problem", "help", "report", "anyone");
  private static final Pattern WI_FI = Pattern.compile("\\bwi[-\\s‑–]+fi\\b");
  private static final Pattern CONNECTIVITY = Pattern.compile(
      "\\b(?:cannot|can['’]t|unable to|could not|won['’]t)\\s+(?:connect|join)\\b"
          + "|\\b(?:not working|no connection|unavailable|disconnected|outage|offline|down)\\b");

  private IncidentSimilarity() {}

  static Set<String> terms(String title, String description) {
    var text = (title + " " + description).toLowerCase(Locale.ROOT);
    text = WI_FI.matcher(text).replaceAll("wifi");
    text = CONNECTIVITY.matcher(text).replaceAll("offline");
    var result = new HashSet<String>();
    var words = TOKEN.matcher(text);
    while (words.find()) {
      var word = words.group();
      if (word.length() >= 3 && !COMMON.contains(word)) result.add(word);
    }
    return result;
  }

  static int score(Set<String> first, Set<String> second) {
    var common = new HashSet<>(first);
    common.retainAll(second);
    if (common.size() < 2) return 0;
    return 100 * common.size() / Math.min(first.size(), second.size());
  }
}
