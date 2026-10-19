import java.net.InetAddress;
import java.net.IDN;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

class StaticCounterexamples {
  public static void main(String[] args) throws Exception {
    Pattern domainPattern = Pattern.compile(Files.readString(Path.of(args[0])).trim());
    for (String address : new String[]{"2a07:a8c0::abcdef", "2a07:a8c1::aaaaaa", "2a07:a8c0::ab:cdef"}) {
      boolean accepted;
      try { accepted = InetAddress.getByName(address).getAddress().length == 16; }
      catch (Exception expected) { accepted = false; }
      System.out.println("IPV6\t" + address + "\t" + accepted);
    }
    for (String domain : new String[]{"example.com", "xn--e1afmkfd.xn--p1ai", "a..com", "a-.com", "-a.com"}) {
      boolean std3;
      try { IDN.toASCII(domain, IDN.USE_STD3_ASCII_RULES); std3 = true; }
      catch (IllegalArgumentException expected) { std3 = false; }
      System.out.println("DOMAIN\t" + domain + "\t" + domainPattern.matcher(domain).matches() + "\t" + std3);
    }
  }
}
