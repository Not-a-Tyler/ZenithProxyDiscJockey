package tyler.discjockey.utils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class SongFinder {

    public static Optional<Song> findBestMatchingSong(String input) {
        NameForms q = NameForms.of(input);

        List<Song> songs = SongLoader.SONGS;
        List<Candidate> candidates = new ArrayList<>(songs.size());
        for (Song s : songs) candidates.add(new Candidate(s));

        // 0) Prefer exact equality on either normalized form
        Optional<Song> exact = candidates.stream()
                .filter(c -> c.name.sp.equals(q.sp) || c.name.ns.equals(q.ns))
                .map(c -> c.song)
                .findFirst();
        if (exact.isPresent()) return exact;

        // 1) startsWith on no-space first (handles user typing without spaces)
        Optional<Song> startsNoSpace = candidates.stream()
                .filter(c -> c.name.ns.startsWith(q.ns))
                .min(Comparator.comparingInt(c -> c.name.ns.length()))
                .map(c -> c.song);
        if (startsNoSpace.isPresent()) return startsNoSpace;

        // 2) startsWith on space-preserved
        Optional<Song> startsSpace = candidates.stream()
                .filter(c -> c.name.sp.startsWith(q.sp))
                .min(Comparator.comparingInt(c -> c.name.sp.length()))
                .map(c -> c.song);
        if (startsSpace.isPresent()) return startsSpace;

        // 3) contains on no-space
        Optional<Song> containsNoSpace = candidates.stream()
                .filter(c -> c.name.ns.contains(q.ns))
                .min(Comparator.comparingInt(c -> c.name.ns.indexOf(q.ns)))
                .map(c -> c.song);
        if (containsNoSpace.isPresent()) return containsNoSpace;

        // 4) contains on space-preserved
        Optional<Song> containsSpace = candidates.stream()
                .filter(c -> c.name.sp.contains(q.sp))
                .min(Comparator.comparingInt(c -> c.name.sp.indexOf(q.sp)))
                .map(c -> c.song);
        if (containsSpace.isPresent()) return containsSpace;

        // 5) Fuzzy rank: Levenshtein on the *no-space* form + tie-breakers
        return candidates.stream()
                .min(Comparator.comparingDouble(c -> score(q, c.name)))
                .map(c -> c.song);
    }

    /** Score lower is better. */
    private static double score(NameForms q, NameForms n) {
        // Base distance on compact/no-space form to avoid space bias
        int d = levenshtein(q.ns, n.ns);

        // Penalize big length differences slightly
        int lenDiff = Math.abs(q.ns.length() - n.ns.length());

        // Reward token hits (query tokens are inferred by splitting candidate; we check if those tokens appear in query as substrings)
        int tokenHits = 0;
        for (String tok : n.tokens) {
            if (tok.length() >= 3 && q.ns.contains(tok)) tokenHits++;
        }

        // Reward longest common substring length (helps “anotheronebitestheduest” vs “…dust” typo)
        int lcsLen = longestCommonSubstringLen(q.ns, n.ns);

        // Final score: distance + 0.25*lenDiff - 0.75*tokenHits - 0.1*lcsLen
        // Clamp to non-negative to keep ordering sane
        double s = d + 0.25 * lenDiff - 0.75 * tokenHits - 0.10 * lcsLen;
        return s < 0 ? 0 : s;
    }

    /** Two normalized forms for matching. */
    private static final class NameForms {
        final String sp;     // space-preserved normalized
        final String ns;     // no-space normalized
        final List<String> tokens;

        private NameForms(String sp, String ns, List<String> tokens) {
            this.sp = sp;
            this.ns = ns;
            this.tokens = tokens;
        }

        static NameForms of(String raw) {
            String x = raw == null ? "" : raw.toLowerCase().trim();

            // strip trailing .nbs
            if (x.endsWith(".nbs")) x = x.substring(0, x.length() - 4);

            // unify separators to space
            x = x.replace('_', ' ').replace('-', ' ');

            // keep only [a-z0-9 ] and collapse spaces
            x = x.replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();

            String sp = x;                   // space-preserved normalized
            String ns = x.replace(" ", "");  // compact/no-space normalized

            List<String> toks = new ArrayList<>();
            if (!sp.isEmpty()) {
                for (String t : sp.split(" ")) if (!t.isEmpty()) toks.add(t);
            }
            return new NameForms(sp, ns, toks);
        }
    }

    private static final class Candidate {
        final Song song;
        final NameForms name;
        Candidate(Song s) {
            this.song = s;
            this.name = NameForms.of(s.displayName);
        }
    }

    /** Classic Levenshtein distance (iterative). */
    private static int levenshtein(String a, String b) {
        int m = a.length(), n = b.length();
        if (m == 0) return n;
        if (n == 0) return m;
        int[] prev = new int[n + 1];
        int[] curr = new int[n + 1];
        for (int j = 0; j <= n; j++) prev[j] = j;
        for (int i = 1; i <= m; i++) {
            curr[0] = i;
            char ca = a.charAt(i - 1);
            for (int j = 1; j <= n; j++) {
                int cost = (ca == b.charAt(j - 1)) ? 0 : 1;
                int ins = curr[j - 1] + 1;
                int del = prev[j] + 1;
                int sub = prev[j - 1] + cost;
                curr[j] = Math.min(Math.min(ins, del), sub);
            }
            int[] tmp = prev; prev = curr; curr = tmp;
        }
        return prev[n];
    }

    /** Longest common contiguous substring length (O(mn)). */
    private static int longestCommonSubstringLen(String a, String b) {
        int m = a.length(), n = b.length();
        if (m == 0 || n == 0) return 0;
        int[] prev = new int[n + 1];
        int[] curr = new int[n + 1];
        int best = 0;
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                if (a.charAt(i - 1) == b.charAt(j - 1)) {
                    curr[j] = prev[j - 1] + 1;
                    if (curr[j] > best) best = curr[j];
                } else {
                    curr[j] = 0;
                }
            }
            int[] tmp = prev; prev = curr; curr = tmp;
        }
        return best;
    }

}
