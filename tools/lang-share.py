# Ajoute les textes du partage de fréquence aux 21 fichiers assets/lang (une clé déjà présente n'est pas ajoutée).
import os
KEYS = ["Share the frequency", "No WebSDR to share", "Invalid share link", "Shared frequency", "Open {0} on {1}?", "{0} on {1}", "Browser: {0}"]
T = {
 "de": ["Frequenz teilen", "Kein WebSDR zum Teilen", "Ungültiger Freigabelink", "Geteilte Frequenz", "{0} auf {1} öffnen?", "{0} auf {1}", "Browser: {0}"],
 "es": ["Compartir la frecuencia", "Ningún WebSDR para compartir", "Enlace compartido no válido", "Frecuencia compartida", "¿Abrir {0} en {1}?", "{0} en {1}", "Navegador: {0}"],
 "it": ["Condividi la frequenza", "Nessun WebSDR da condividere", "Link di condivisione non valido", "Frequenza condivisa", "Aprire {0} su {1}?", "{0} su {1}", "Browser: {0}"],
 "nl": ["Frequentie delen", "Geen WebSDR om te delen", "Ongeldige deellink", "Gedeelde frequentie", "{0} openen op {1}?", "{0} op {1}", "Browser: {0}"],
 "pt": ["Partilhar a frequência", "Nenhum WebSDR para partilhar", "Link de partilha inválido", "Frequência partilhada", "Abrir {0} em {1}?", "{0} em {1}", "Navegador: {0}"],
 "pl": ["Udostępnij częstotliwość", "Brak WebSDR do udostępnienia", "Nieprawidłowy link udostępniania", "Udostępniona częstotliwość", "Otworzyć {0} na {1}?", "{0} na {1}", "Przeglądarka: {0}"],
 "cs": ["Sdílet frekvenci", "Žádné WebSDR ke sdílení", "Neplatný odkaz ke sdílení", "Sdílená frekvence", "Otevřít {0} na {1}?", "{0} na {1}", "Prohlížeč: {0}"],
 "sk": ["Zdieľať frekvenciu", "Žiadne WebSDR na zdieľanie", "Neplatný odkaz na zdieľanie", "Zdieľaná frekvencia", "Otvoriť {0} na {1}?", "{0} na {1}", "Prehliadač: {0}"],
 "sl": ["Deli frekvenco", "Ni WebSDR za deljenje", "Neveljavna povezava za deljenje", "Deljena frekvenca", "Odprem {0} na {1}?", "{0} na {1}", "Brskalnik: {0}"],
 "hr": ["Podijeli frekvenciju", "Nema WebSDR-a za dijeljenje", "Neispravna poveznica za dijeljenje", "Podijeljena frekvencija", "Otvoriti {0} na {1}?", "{0} na {1}", "Preglednik: {0}"],
 "hu": ["Frekvencia megosztása", "Nincs megosztható WebSDR", "Érvénytelen megosztási hivatkozás", "Megosztott frekvencia", "Megnyitja: {0} – {1}?", "{0} – {1}", "Böngésző: {0}"],
 "ro": ["Partajează frecvența", "Niciun WebSDR de partajat", "Link de partajare nevalid", "Frecvență partajată", "Deschideți {0} pe {1}?", "{0} pe {1}", "Browser: {0}"],
 "bg": ["Сподели честотата", "Няма WebSDR за споделяне", "Невалидна връзка за споделяне", "Споделена честота", "Да се отвори ли {0} на {1}?", "{0} на {1}", "Браузър: {0}"],
 "el": ["Κοινοποίηση συχνότητας", "Κανένα WebSDR για κοινοποίηση", "Μη έγκυρος σύνδεσμος κοινοποίησης", "Κοινόχρηστη συχνότητα", "Άνοιγμα {0} στα {1};", "{0} στα {1}", "Πρόγραμμα περιήγησης: {0}"],
 "sv": ["Dela frekvensen", "Ingen WebSDR att dela", "Ogiltig delningslänk", "Delad frekvens", "Öppna {0} på {1}?", "{0} på {1}", "Webbläsare: {0}"],
 "da": ["Del frekvensen", "Ingen WebSDR at dele", "Ugyldigt delingslink", "Delt frekvens", "Åbn {0} på {1}?", "{0} på {1}", "Browser: {0}"],
 "nb": ["Del frekvensen", "Ingen WebSDR å dele", "Ugyldig delingslenke", "Delt frekvens", "Åpne {0} på {1}?", "{0} på {1}", "Nettleser: {0}"],
 "fi": ["Jaa taajuus", "Ei jaettavaa WebSDR:ää", "Virheellinen jakolinkki", "Jaettu taajuus", "Avataanko {0}: {1}?", "{0} – {1}", "Selain: {0}"],
 "et": ["Jaga sagedust", "Pole WebSDR-i, mida jagada", "Vigane jagamislink", "Jagatud sagedus", "Kas avada {0}: {1}?", "{0} – {1}", "Brauser: {0}"],
 "lv": ["Kopīgot frekvenci", "Nav WebSDR, ko kopīgot", "Nederīga kopīgošanas saite", "Kopīgotā frekvence", "Atvērt {0}: {1}?", "{0} – {1}", "Pārlūks: {0}"],
 "lt": ["Bendrinti dažnį", "Nėra WebSDR bendrinimui", "Netinkama bendrinimo nuoroda", "Bendrintas dažnis", "Atidaryti {0}: {1}?", "{0} – {1}", "Naršyklė: {0}"],
}
d = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "lang")
for code, vals in T.items():
    p = os.path.join(d, code + ".txt")
    with open(p, encoding="utf-8") as f: have = {l.split("\t", 1)[0] for l in f.read().splitlines() if "\t" in l}
    add = [k + "\t" + v for k, v in zip(KEYS, vals) if k not in have]
    if add:
        with open(p, "a", encoding="utf-8", newline="\n") as f: f.write("\n".join(add) + "\n")
    print(code, len(add))
