package be.ilyteq.escape.core;

public enum Phase {
    LOBBY,          // jeu pas encore lancé
    TRANSITION,     // changement de salle (round = manche à venir)
    ROUND,          // manche en cours
    VALIDATION,     // le maître du jeu valide les scores (round = manche terminée)
    FINAL_TRANSIT,  // rejoindre la salle de la finale
    FINAL,          // épreuve finale
    FINISHED
}
