package com.zuunr.mermaidschema;

import com.zuunr.json.JsonObject;
import com.zuunr.json.JsonValue;
import com.zuunr.json.JsonValueFactory;
import freemarker.template.Configuration;
import freemarker.template.Template;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MermaidController {

    private Configuration freemarkerConf;

    public MermaidController(@Autowired Configuration freemarkerConf) {
        this.freemarkerConf = freemarkerConf;
    }

    @GetMapping(value = "/class-diagram", produces = "text/html")
    public HttpServletResponse getClassDiagram(HttpServletResponse response) {
        try {

            /* ------------------------------------------------------------------------ */
            /* You usually do these for MULTIPLE TIMES in the application life-cycle:   */

            /* Create a data-model */
            JsonValue schema = JsonValueFactory.create("""
                        {
                             "$schema": "https://json-schema.org/draft/2020-12/schema",
                             "title": "AviseringPoster",
                             "type": "object",
                             "properties": {
                               "aviseringsposter": {
                                 "type": "array",
                                 "items": {
                                   "$ref": "#/$defs/AviseringPost"
                                 }
                               }
                             },
                             "$defs": {
                               "AviseringPost": {
                                 "type": "object",
                                 "description": "En komplett post med personuppgifter",
                                 "required": [
                                   "personId",
                                   "sekretessmarkering",
                                   "skyddadFolkbokforing"
                                 ],
                                 "properties": {
                                   "personId": {
                                     "$ref": "#/$defs/PersonId"
                                   },
                                   "sekretessmarkering": {
                                     "$ref": "#/$defs/Sekretessmarkering"
                                   },
                                   "sekretessDatum": {
                                     "$ref": "#/$defs/SekretessDatum"
                                   },
                                   "skyddadFolkbokforing": {
                                     "$ref": "#/$defs/JaNejTYPE"
                                   },
                                   "skyddadFolkbokforingDatum": {
                                     "$ref": "#/$defs/SkyddadFolkbokforingDatum"
                                   },
                                   "senasteAndringSPAR": {
                                     "$ref": "#/$defs/SparDatumTYPE"
                                   },
                                   "summeradInkomst": {
                                     "$ref": "#/$defs/SummeradInkomstTYPE"
                                   },
                                   "inkomstAr": {
                                     "$ref": "#/$defs/InkomstArTYPE"
                                   },
                                   "namn": {
                                     "type": "array",
                                     "items": {
                                       "$ref": "#/$defs/NamnTYPE"
                                     }
                                   },
                                   "persondetaljer": {
                                     "type": "array",
                                     "items": {
                                       "$ref": "#/$defs/PersondetaljerTYPE"
                                     }
                                   },
                                   "utvandrad": {
                                     "$ref": "#/$defs/Utvandrad"
                                   },
                                   "folkbokforing": {
                                     "type": "array",
                                     "items": {
                                       "$ref": "#/$defs/Folkbokforing"
                                     }
                                   },
                                   "folkbokforingsadresser": {
                                     "type": "array",
                                     "items": {
                                       "$ref": "#/$defs/FolkbokforingsadressTYPE"
                                     }
                                   },
                                   "sarskildaPostadresser": {
                                     "type": "array",
                                     "items": {
                                       "$ref": "#/$defs/SarskildPostadressTYPE"
                                     }
                                   },
                                   "utlandsadress": {
                                     "type": "array",
                                     "items": {
                                       "$ref": "#/$defs/InternationellAdress"
                                     }
                                   },
                                   "kontaktadresser": {
                                     "type": "array",
                                     "items": {
                                       "$ref": "#/$defs/Kontaktadress"
                                     }
                                   },
                                   "relationer": {
                                     "type": "array",
                                     "items": {
                                       "$ref": "#/$defs/RelationTYPE"
                                     }
                                   },
                                   "fastigheter": {
                                     "type": "array",
                                     "items": {
                                       "$ref": "#/$defs/FastighetTYPE"
                                     }
                                   }
                                 }
                               },
                               "Avregistrering": {
                                 "type": "object",
                                 "properties": {
                                   "AvregistreringsorsakKod": {
                                     "$ref": "#/$defs/AvregistreringsorsakKodTYPE"
                                   },
                                   "Avlidendatum": {
                                     "$ref": "#/$defs/datumtid:SparOfullstandigtDatumTYPE"
                                   },
                                   "AntraffadDodDatum": {
                                     "$ref": "#/$defs/datumtid:SparOfullstandigtDatumTYPE"
                                   },
                                   "Avregistreringsdatum": {
                                     "$ref": "#/$defs/datumtid:SparOfullstandigtDatumTYPE"
                                   }
                                 },
                                 "required": [
                                   "AvregistreringsorsakKod"
                                 ]
                               },
                               "Person": {
                                 "title": "Person",
                                 "type": "object",
                                 "properties": {
                                   "PersonId": {
                                     "$ref": "#/$defs/PersonId"
                                   },
                                   "SenasteAndringSPAR": {
                                     "$ref": "#/$defs/datumtid:SparDatumTYPE"
                                   }
                                 }
                               },
                               "PersonId": {
                                 "type": "object",
                                 "required": [
                                   "idNummer",
                                   "typ"
                                 ],
                                 "properties": {
                                   "idNummer": {
                                     "$ref": "#/$defs/IdNummerTYPE"
                                   },
                                   "typ": {
                                     "$ref": "#/$defs/TypTYPE"
                                   }
                                 }
                               },
                               "IdNummerTYPE": {
                                 "type": "string",
                                 "description": "Person-, samordnings- eller immunitetsnummer",
                                 "pattern": "^[1-9][0-9]{11}$"
                               },
                               "TypTYPE": {
                                 "type": "string",
                                 "description": "Anger vilken typ IdNummer avser",
                                 "enum": [
                                   "PERSONNUMMER",
                                   "SAMORDNINGSNUMMER",
                                   "IMMUNITETSNUMMER"
                                 ]
                               },
                               "datumtid:SparDatumTYPE": {
                                 "$comment": "Referenced type from 'datumtid' namespace. Define its schema under $defs if needed."
                               },
                               "AvregistreringsorsakKodTYPE": {
                                 "type": "string",
                                 "description": "Kod som anger avregistreringsorsak. I allmänhet en tvåställig kod. I äldre uppgifter används enställiga koder, som: A - Avliden, G - Gammalt nummer, O - Övrig orsak.",
                                 "minLength": 1,
                                 "maxLength": 2
                               },
                               "SparOfullstandigtDatumTYPE": {
                                 "type": "string",
                                 "description": "Datum kan vara ofullständigt. Ex: YYYY-MM-DD, YYYY-MM, YYYY, etc.",
                                 "examples": [
                                   "2019-01-01",
                                   "2019-01-00",
                                   "2019-00-00",
                                   "0000-00-00",
                                   "200101",
                                   "2001",
                                   "0"
                                 ]
                               },
                               "TidsstampelTYPE": {
                                 "type": "string",
                                 "format": "date-time",
                                 "pattern": "^\\\\d{4}-\\\\d{2}-\\\\d{2}T\\\\d{2}:\\\\d{2}:\\\\d{2}\\\\.\\\\d{3}$",
                                 "description": "Datum och tid med millisekunder på formatet YYYY-MM-DDThh:mm:ss.nnn."
                               },
                               "DatumTid": {
                                 "type": "object",
                                 "properties": {
                                   "datumFrom": {
                                     "$ref": "#/definitions/SparDatumTYPE"
                                   },
                                   "datumTill": {
                                     "$ref": "#/definitions/SparDatumTYPE"
                                   },
                                   "DatumIntervall": {
                                     "$ref": "#/$defs/DatumIntervall"
                                   },
                                   "Tidsstampel": {
                                     "$ref": "#/definitions/TidsstampelTYPE"
                                   }
                                 }
                               },
                               "CareOfTYPE": {
                                 "type": "string",
                                 "minLength": 1,
                                 "maxLength": 35
                               },
                               "UtdelningsadressTYPE": {
                                 "type": "string",
                                 "minLength": 1,
                                 "maxLength": 35
                               },
                               "PostNrTYPE": {
                                 "type": "string",
                                 "pattern": "^[1-9][0-9]{4}$"
                               },
                               "PostortTYPE": {
                                 "type": "string",
                                 "minLength": 1,
                                 "maxLength": 27
                               },
                               "LandTYPE": {
                                 "type": "string",
                                 "minLength": 1,
                                 "maxLength": 35
                               },
                               "SvenskAdressTYPE": {
                                 "type": "object",
                                 "properties": {
                                   "datumIntervall": {
                                     "$ref": "http://statenspersonadressregister.se/schema/komponent/generellt/datumtid-1.1#/$defs/DatumIntervall"
                                   },
                                   "careOf": {
                                     "$ref": "#/$defs/CareOfTYPE"
                                   },
                                   "utdelningsadress1": {
                                     "$ref": "#/$defs/UtdelningsadressTYPE"
                                   },
                                   "utdelningsadress2": {
                                     "$ref": "#/$defs/UtdelningsadressTYPE"
                                   },
                                   "postNr": {
                                     "$ref": "#/$defs/PostNrTYPE"
                                   },
                                   "postort": {
                                     "$ref": "#/$defs/PostortTYPE"
                                   }
                                 },
                                 "required": []
                               },
                               "DeladeAdressElement": {
                                 "type": "object",
                                 "properties": {
                                   "CareOf": {
                                     "$ref": "#/$defs/CareOfTYPE"
                                   },
                                   "Utdelningsadress1": {
                                     "$ref": "#/$defs/UtdelningsadressTYPE"
                                   },
                                   "Utdelningsadress2": {
                                     "$ref": "#/$defs/UtdelningsadressTYPE"
                                   },
                                   "Utdelningsadress3": {
                                     "$ref": "#/$defs/UtdelningsadressTYPE"
                                   },
                                   "PostNr": {
                                     "$ref": "#/$defs/PostNrTYPE"
                                   },
                                   "Postort": {
                                     "$ref": "#/$defs/PostortTYPE"
                                   },
                                   "Land": {
                                     "$ref": "#/$defs/LandTYPE"
                                   },
                                   "SvenskAdress": {
                                     "$ref": "#/$defs/SvenskAdressTYPE"
                                   },
                                   "InternationellAdress": {
                                     "$ref": "#/$defs/InternationellAdressTYPE"
                                   },
                                   "KundNrTYPE": {
                                     "type": "integer",
                                     "description": "Kundnummer som tilldelats av SPAR",
                                     "minimum": 500001,
                                     "maximum": 599999
                                   },
                                   "UppdragIdTYPE": {
                                     "type": "integer",
                                     "minimum": 1,
                                     "description": "ID för uppdrag"
                                   }
                                 }
                               },
                               "LanKodTYPE": {
                                 "type": "string",
                                 "pattern": "^[0-9]{2}$",
                                 "description": "LanKod must be exactly two digits."
                               },
                               "KommunKodTYPE": {
                                 "type": "string",
                                 "pattern": "^[0-9]{2}$",
                                 "description": "KommunKod must be exactly two digits."
                               },
                               "DistriktKodTYPE": {
                                 "type": "string",
                                 "pattern": "^[1-9][0-9]{5}$",
                                 "description": "DistriktKod must be a six-digit number, starting with a non-zero digit."
                               },
                               "HemvistTYPE": {
                                 "type": "string",
                                 "enum": [
                                   "Skriven på adressen",
                                   "På kommunen skriven",
                                   "Utan känt hemvist"
                                 ],
                                 "description": "Possible values for Hemvist."
                               },
                               "DatumIntervall": {
                                 "type": "object",
                                 "properties": {
                                   "datumFrom": {
                                     "$ref": "#/$defs/SparDatumTYPE"
                                   },
                                   "datumTill": {
                                     "$ref": "#/$defs/SparDatumTYPE"
                                   }
                                 },
                                 "required": [
                                   "datumFrom",
                                   "datumTill"
                                 ],
                                 "description": "Elementen DatumFrom och DatumTill visas enbart då historik används. DatumTill 9999-12-31 markerar aktuell post."
                               },
                               "Folkbokforing": {
                                 "type": "object",
                                 "properties": {
                                   "datumIntervall": {
                                     "$ref": "#/$defs/DatumIntervall"
                                   },
                                   "folkbokfordLanKod": {
                                     "$ref": "#/$defs/LanKodTYPE"
                                   },
                                   "folkbokfordKommunKod": {
                                     "$ref": "#/$defs/KommunKodTYPE"
                                   },
                                   "hemvist": {
                                     "$ref": "#/$defs/HemvistTYPE"
                                   },
                                   "folkbokforingsdatum": {
                                     "$ref": "#/$defs/SparDatumTYPE"
                                   },
                                   "distriktKod": {
                                     "$ref": "#/$defs/DistriktKodTYPE"
                                   }
                                 },
                                 "additionalProperties": false
                               },
                               "FolkbokforingsadressTYPE": {
                                 "type": "object",
                                 "properties": {
                                   "svenskAdress": {
                                     "$ref": "#/$defs/SvenskAdress"
                                   }
                                 },
                                 "required": [
                                   "svenskAdress"
                                 ]
                               },
                               "Hanvisning": {
                                 "type": "object",
                                 "properties": {
                                   "idNummer": {
                                     "$ref": "#/$defs/IdNummer"
                                   },
                                   "typ": {
                                     "type": "string",
                                     "enum": [
                                       "Till",
                                       "Fran"
                                     ],
                                     "description": "Typ anger:\\n* Till: Personen har bytt id-nummer till ett nytt nummmer. Hänvisningen anger det nya id-numret.\\n* Fran: Personen har bytt id-nummer från ett gammalt nummer. Hänvisningen anger det gamla id-numret.\\n* inget värde: Personen har eller har haft ett annat id-nummer. Hänvisningen anger detta id-nummer."
                                   }
                                 },
                                 "required": [
                                   "idNummer"
                                 ],
                                 "additionalProperties": false
                               },
                               "PersonId": {
                                 "type": "object",
                                 "required": [
                                   "idNummer",
                                   "typ"
                                 ],
                                 "properties": {
                                   "idNummer": {
                                     "$ref": "#/$defs/IdNummerTYPE"
                                   },
                                   "typ": {
                                     "$ref": "#/$defs/TypTYPE"
                                   }
                                 }
                               },
                               "IdNummerTYPE": {
                                 "type": "string",
                                 "description": "Person-, samordnings- eller immunitetsnummer",
                                 "pattern": "^[1-9][0-9]{11}$"
                               },
                               "TypTYPE": {
                                 "type": "string",
                                 "description": "Anger vilken typ IdNummer avser",
                                 "enum": [
                                   "PERSONNUMMER",
                                   "SAMORDNINGSNUMMER",
                                   "IMMUNITETSNUMMER"
                                 ]
                               },
                               "SekretessmarkeringMedAttributTYPE": {
                                 "type": "object",
                                 "properties": {
                                   "value": {
                                     "$ref": "#/$defs/typ_JaNejTYPE"
                                   },
                                   "sattAvSPAR": {
                                     "$ref": "#/$defs/SekretessSattAvSPARTYPE"
                                   }
                                 },
                                 "required": [
                                   "value"
                                 ]
                               },
                               "SekretessSattAvSPARTYPE": {
                                 "type": "string",
                                 "enum": [
                                   "JA"
                                 ],
                                 "description": "Anger att sekretessmarkeringen är satt av SPAR. Sker endast om en personpost inkommer till SPAR från Folkbokföringen med Skyddad folkbokföring, men utan Sekretessmarkering."
                               },
                               "typ_JaNejTYPE": {
                                 "$ref": "#/$defs/JaNejTYPE"
                               },
                               "datumtid_SparDatumTYPE": {
                                 "$ref": "#/$defs/SparDatumTYPE"
                               },
                               "Sekretessmarkering": {
                                 "$ref": "#/$defs/SekretessmarkeringMedAttributTYPE"
                               },
                               "SekretessDatum": {
                                 "$ref": "#/$defs/datumtid_SparDatumTYPE"
                               },
                               "SkyddadFolkbokforing": {
                                 "$ref": "#/$defs/typ_JaNejTYPE"
                               },
                               "SkyddadFolkbokforingDatum": {
                                 "$ref": "#/$defs/datumtid_SparDatumTYPE"
                               },
                               "SummeradInkomstTYPE": {
                                 "type": "string",
                                 "description": "Summerad inkomst i SEK",
                                 "pattern": "^[0-9]{1,11}$"
                               },
                               "InkomstArTYPE": {
                                 "type": "string",
                                 "description": "Inkomstår för summerad inkomst",
                                 "pattern": "^2[0-9]{3}$"
                               },
                               "NamnTYPE": {
                                 "type": "object",
                                 "properties": {
                                   "datumIntervall": {
                                     "$ref": "#/$defs/DatumIntervall"
                                   },
                                   "aviseringsnamn": {
                                     "$ref": "#/$defs/Aviseringsnamn"
                                   },
                                   "fornamn": {
                                     "$ref": "#/$defs/Fornamn"
                                   },
                                   "tilltalsnamn": {
                                     "$ref": "#/$defs/Tilltalsnamn"
                                   },
                                   "mellannamn": {
                                     "$ref": "#/$defs/Mellannamn"
                                   },
                                   "efternamn": {
                                     "$ref": "#/$defs/Efternamn"
                                   }
                                 },
                                 "additionalProperties": false
                               },
                               "Aviseringsnamn": {
                                 "description": "Reference to namn:Aviseringsnamn",
                                 "$comment": "Add the appropriate definition for Aviseringsnamn here."
                               },
                               "Fornamn": {
                                 "description": "Reference to namn:Fornamn",
                                 "type": "string",
                                 "minLength": 1,
                                 "maxLength": 80
                               },
                               "Tilltalsnamn": {
                                 "description": "Reference to namn:Tilltalsnamn",
                                 "$comment": "Add the appropriate definition for Tilltalsnamn here."
                               },
                               "Mellannamn": {
                                 "type": "string",
                                 "minLength": 1,
                                 "maxLength": 40
                               },
                               "Efternamn": {
                                 "type": "string",
                                 "minLength": 1,
                                 "maxLength": 60
                               },
                               "PersondetaljerTYPE": {
                                 "type": "object",
                                 "description": "Detailed information directly related to a person.",
                                 "properties": {
                                   "datumintervall": {
                                     "type": "object",
                                     "$ref": "#/$defs/DatumIntervall"
                                   },
                                   "sekretessmarkering": {
                                     "$ref": "#/$defs/Sekretessmarkering"
                                   },
                                   "skyddadFolkbokforing": {
                                     "$ref": "#/$defs/SkyddadFolkbokforing"
                                   },
                                   "avregistreringsorsakKod": {
                                     "$ref": "#/$defs/AvregistreringsorsakKod"
                                   },
                                   "avregistreringsdatum": {
                                     "$ref": "#/$defs/Avregistreringsdatum"
                                   },
                                   "avlidendatum": {
                                     "$ref": "#/$defs/Avlidendatum"
                                   },
                                   "antraffadDodDatum": {
                                     "$ref": "#/$defs/AntraffadDodDatum"
                                   },
                                   "fodelsedatum": {
                                     "$ref": "#/$defs/Fodelsedatum"
                                   },
                                   "fodelselanKod": {
                                     "$ref": "#/$defs/FodelselanKodTYPE"
                                   },
                                   "fodelseforsamling": {
                                     "$ref": "#/$defs/FodelseforsamlingTYPE"
                                   },
                                   "kon": {
                                     "$ref": "#/$defs/KonTYPE"
                                   },
                                   "svenskMedborgare": {
                                     "$ref": "#/$defs/JaNejTYPE"
                                   },
                                   "hanvisning": {
                                     "type": "array",
                                     "items": {
                                       "$ref": "#/$defs/Hanvisning"
                                     }
                                   },
                                   "snIdentitetsniva": {
                                     "$ref": "#/$defs/SnIdentitetsniva"
                                   },
                                   "snIdentitetsnivaDatum": {
                                     "$ref": "#/$defs/SnIdentitetsnivaDatum"
                                   },
                                   "snStatus": {
                                     "$ref": "#/$defs/SnStatus"
                                   },
                                   "snTilldelningsdatum": {
                                     "$ref": "#/$defs/SnTilldelningsdatum"
                                   },
                                   "snPreliminartVilandeforklaringsdatum": {
                                     "$ref": "#/$defs/SnPreliminartVilandeforklaringsdatum"
                                   },
                                   "snFornyelsedatum": {
                                     "$ref": "#/$defs/SnFornyelsedatum"
                                   },
                                   "snVilandeorsak": {
                                     "$ref": "#/$defs/SnVilandeorsak"
                                   },
                                   "snVilandeforklaringsdatum": {
                                     "$ref": "#/$defs/SnVilandeforklaringsdatum"
                                   },
                                   "snAvlidendatum": {
                                     "$ref": "#/$defs/SnAvlidendatum"
                                   }
                                 },
                                 "additionalProperties": false
                               },
                               "Fodelsedatum": {
                                 "$ref": "#/$defs/SparDatumTYPE"
                               },
                               "FodelselanKodTYPE": {
                                 "type": "string",
                                 "pattern": "^[0-9]{2}$",
                                 "description": "Länskod för födelselän"
                               },
                               "FodelseforsamlingTYPE": {
                                 "type": "string",
                                 "minLength": 1,
                                 "maxLength": 30,
                                 "description": "Födelseförsamling i fritext"
                               },
                               "AntraffadDodDatum": {
                                 "$ref": "#/$defs/SparOfullstandigtDatumTYPE"
                               },
                               "SnIdentitetsniva": {
                                 "type": "string",
                                 "enum": [
                                   "STYRKT",
                                   "SANNOLIK",
                                   "OSÄKER",
                                   "INTE_TILLAMPLIG"
                                 ]
                               },
                               "SnIdentitetsnivaDatum": {
                                 "$ref": "#/$defs/SparOfullstandigtDatumTYPE"
                               },
                               "SnStatus": {
                                 "description": "SnStatus definition from sn namespace"
                               },
                               "SnTilldelningsdatum": {
                                 "$ref": "#/$defs/SparOfullstandigtDatumTYPE"
                               },
                               "SnPreliminartVilandeforklaringsdatum": {
                                 "description": "SnPreliminartVilandeforklaringsdatum definition from sn namespace"
                               },
                               "SnFornyelsedatum": {
                                 "$ref": "#/$defs/SparOfullstandigtDatumTYPE"
                               },
                               "SnVilandeorsak": {
                                 "description": "SnVilandeorsak definition from sn namespace"
                               },
                               "SnVilandeforklaringsdatum": {
                                 "$ref": "#/$defs/SparOfullstandigtDatumTYPE"
                               },
                               "SnAvlidendatum": {
                                 "$ref": "#/$defs/SparOfullstandigtDatumTYPE"
                               },
                               "SparDatumTYPE": {
                                 "type": "string",
                                 "format": "date",
                                 "description": "Ett datum på format YYYY-MM-DD"
                               },
                               "KonTYPE": {
                                 "description": "KonTYPE definition from typ namespace"
                               },
                               "JaNejTYPE": {
                                 "description": "JaNejTYPE definition from typ namespace",
                                 "enum": [
                                   "JA",
                                   "NEJ"
                                 ]
                               },
                               "Utvandrad": {
                                 "type": "object",
                                 "required": [],
                                 "properties": {
                                   "aterinlasningsdatum": {
                                     "$ref": "#/$defs/SparDatumTYPE",
                                     "description": "Datum då personposten återinlästes i SPAR. Gäller utvandrade personer som gallrats från SPAR och sedan återinlästs."
                                   },
                                   "forlangningsdatum": {
                                     "$ref": "#/$defs/SparDatumTYPE",
                                     "description": "Datum som gallringstid räknas från. För utvandrade personer som fått förlängd tid till gallring i SPAR."
                                   }
                                 },
                                 "additionalProperties": false,
                                 "description": "Information om utvandrad person."
                               },
                               "SarskildPostadressTYPE": {
                                 "type": "object",
                                 "description": "Represents the SärskildPostadress type",
                                 "properties": {
                                   "svenskadress": {
                                     "$ref": "#/$defs/SvenskAdress"
                                   },
                                   "internationellAdress": {
                                     "$ref": "#/$defs/InternationellAdressTYPE"
                                   }
                                 },
                                 "minProperties": 1,
                                 "maxProperties": 2
                               },
                               "SvenskAdress": {
                                 "description": "Placeholder for the definition of SvenskAdress",
                                 "$ref": "#/$defs/SvenskAdressTYPE"
                               },
                               "Utlandsadress": {
                                 "type": "object",
                                 "properties": {
                                   "InternationellAdress": {
                                     "$ref": "#/$defs/InternationellAdress"
                                   }
                                 },
                                 "required": [
                                   "InternationellAdress"
                                 ],
                                 "additionalProperties": false
                               },
                               "Kontaktadress": {
                                 "title": "Kontaktadress",
                                 "description": "Adress för att kontakta person med samordningsnummer",
                                 "type": "object",
                                 "properties": {
                                   "svenskAdress": {
                                     "$ref": "#/$defs/SvenskAdress"
                                   },
                                   "internationellAdress": {
                                     "$ref": "#/$defs/InternationellAdress"
                                   }
                                 }
                               },
                               "RelationTYPE": {
                                 "type": "object",
                                 "properties": {
                                   "datumintervall": {
                                     "$ref": "#/$defs/DatumIntervall"
                                   },
                                   "relationstyp": {
                                     "$ref": "#/$defs/RelationstypTYPE"
                                   },
                                   "idNummer": {
                                     "$ref": "#/$defs/IdNummer"
                                   },
                                   "fornamn": {
                                     "$ref": "#/$defs/Fornamn"
                                   },
                                   "mellannamn": {
                                     "$ref": "#/$defs/Mellannamn"
                                   },
                                   "efternamn": {
                                     "$ref": "#/$defs/Efternamn"
                                   },
                                   "fodelsetid": {
                                     "$ref": "#/$defs/SparDatumTYPE"
                                   },
                                   "avregistreringsorsakKod": {
                                     "$ref": "#/$defs/AvregistreringsorsakKod"
                                   },
                                   "avregistreringsdatum": {
                                     "$ref": "#/$defs/Avregistreringsdatum"
                                   },
                                   "avlidendatum": {
                                     "$ref": "#/$defs/Avlidendatum"
                                   }
                                 },
                                 "required": [
                                   "relationstyp"
                                 ],
                                 "additionalProperties": false
                               },
                               "RelationstypTYPE": {
                                 "type": "string",
                                 "enum": [
                                   "VÅRDNADSHAVARE",
                                   "MAKE/MAKA/REGISTRERAD PARTNER"
                                 ],
                                 "description": "Typ av relation"
                               },
                               "IdNummer": {
                                 "$comment": "Referenced from 'person:IdNummer'. Define it within $defs."
                               },
                               "AvregistreringsorsakKod": {
                                 "type": "string",
                                 "minLength": 1,
                                 "maxLength": 2
                               },
                               "Avregistreringsdatum": {
                                 "$ref": "#/$defs/SparOfullstandigtDatumTYPE"
                               },
                               "Avlidendatum": {
                                 "$ref": "#/$defs/SparOfullstandigtDatumTYPE"
                               },
                               "FastighetTYPE": {
                                 "type": "object",
                                 "description": "Sammanhållande element för en fastighet som personen äger hela eller delar av",
                                 "properties": {
                                   "taxeringsenhetsnummer": {
                                     "$ref": "#/$defs/TaxeringsenhetsnummerTYPE"
                                   },
                                   "lanKod": {
                                     "$ref": "#/$defs/FastighetLanKodTYPE"
                                   },
                                   "kommunKod": {
                                     "$ref": "#/$defs/FastighetKommunKodTYPE"
                                   },
                                   "fastighetKod": {
                                     "$ref": "#/$defs/FastighetKodTYPE"
                                   },
                                   "taxeringsar": {
                                     "$ref": "#/$defs/TaxeringsarTYPE"
                                   },
                                   "taxeringsvarde": {
                                     "$ref": "#/$defs/TaxeringsvardeTYPE"
                                   },
                                   "fastighetDel": {
                                     "type": "array",
                                     "items": {
                                       "$ref": "#/$defs/FastighetDelTYPE"
                                     }
                                   }
                                 },
                                 "required": [
                                   "taxeringsenhetsnummer",
                                   "lanKod",
                                   "kommunKod",
                                   "fastighetKod",
                                   "fastighetDel"
                                 ]
                               },
                               "FastighetDelTYPE": {
                                 "type": "object",
                                 "description": "Sammanhållande element för en fastighet som personen äger hela eller delar av",
                                 "properties": {
                                   "taxeringsidentitet": {
                                     "$ref": "#/$defs/TaxeringsidentitetTYPE"
                                   },
                                   "fastighetBeteckning": {
                                     "$ref": "#/$defs/FastighetBeteckningTYPE"
                                   },
                                   "andelstalTaljare": {
                                     "$ref": "#/$defs/AndelstalTaljareTYPE"
                                   },
                                   "andelstalNamnare": {
                                     "$ref": "#/$defs/AndelstalNamnareTYPE"
                                   }
                                 },
                                 "required": [
                                   "taxeringsidentitet",
                                   "fastighetBeteckning",
                                   "andelstalTaljare",
                                   "andelstalNamnare"
                                 ]
                               },
                               "FastighetKodTYPE": {
                                 "type": "string",
                                 "description": "Kod för typ av fastighet. För information om koder, se https://www.statenspersonadressregister.se",
                                 "pattern": "^[0-9]{3}$"
                               },
                               "FastighetLanKodTYPE": {
                                 "type": "string",
                                 "description": "Länskod där fastigheten är belägen",
                                 "pattern": "^[0-9]{2}$"
                               },
                               "FastighetKommunKodTYPE": {
                                 "type": "string",
                                 "description": "Kommunkod i det län där fastigheten är belägen",
                                 "pattern": "^[0-9]{2}$"
                               },
                               "TaxeringsarTYPE": {
                                 "type": "string",
                                 "description": "Taxeringsår som taxeringsvärdet avser",
                                 "pattern": "^2[0-9]{3}$"
                               },
                               "TaxeringsvardeTYPE": {
                                 "type": "string",
                                 "description": "Taxeringsvärde angivet taxeringsår i SEK",
                                 "pattern": "^[0-9]{1,11}$"
                               },
                               "AndelstalTaljareTYPE": {
                                 "type": "integer",
                                 "description": "Täljare för ägarandel i fastighetsdelen"
                               },
                               "AndelstalNamnareTYPE": {
                                 "type": "integer",
                                 "description": "Nämnare för ägarandel i fastighetsdelen"
                               },
                               "FastighetBeteckningTYPE": {
                                 "type": "string",
                                 "description": "Fastighetsbeteckning"
                               },
                               "TaxeringsidentitetTYPE": {
                                 "type": "string",
                                 "description": "Taxeringsidentitet"
                               },
                               "TaxeringsenhetsnummerTYPE": {
                                 "type": "string",
                                 "description": "Taxeringsenhetsnummer"
                               },
                               "InternationellAdress": {
                                 "description": "Placeholder for the InternationellAdress type definition.",
                                 "type": "object"
                               },
                               "CareOfTYPE": {
                                 "type": "string",
                                 "minLength": 1,
                                 "maxLength": 35
                               },
                               "UtdelningsadressTYPE": {
                                 "type": "string",
                                 "minLength": 1,
                                 "maxLength": 35
                               },
                               "PostNrTYPE": {
                                 "type": "string",
                                 "pattern": "^[1-9][0-9]{4}$"
                               },
                               "PostortTYPE": {
                                 "type": "string",
                                 "minLength": 1,
                                 "maxLength": 27
                               },
                               "LandTYPE": {
                                 "type": "string",
                                 "minLength": 1,
                                 "maxLength": 35
                               },
                               "SvenskAdressTYPE": {
                                 "type": "object",
                                 "properties": {
                                   "DatumIntervall": {
                                     "$ref": "#/$defs/DatumIntervall"
                                   },
                                   "CareOf": {
                                     "$ref": "#/$defs/CareOfTYPE"
                                   },
                                   "Utdelningsadress1": {
                                     "$ref": "#/$defs/UtdelningsadressTYPE"
                                   },
                                   "Utdelningsadress2": {
                                     "$ref": "#/$defs/UtdelningsadressTYPE"
                                   },
                                   "PostNr": {
                                     "$ref": "#/$defs/PostNrTYPE"
                                   },
                                   "Postort": {
                                     "$ref": "#/$defs/PostortTYPE"
                                   }
                                 },
                                 "required": []
                               },
                               "InternationellAdressTYPE": {
                                 "type": "object",
                                 "properties": {
                                   "datumIntervall": {
                                     "$ref": "#/$defs/DatumIntervall"
                                   },
                                   "utdelningsadress1": {
                                     "$ref": "#/$defs/UtdelningsadressTYPE"
                                   },
                                   "utdelningsadress2": {
                                     "$ref": "#/$defs/UtdelningsadressTYPE"
                                   },
                                   "utdelningsadress3": {
                                     "$ref": "#/$defs/UtdelningsadressTYPE"
                                   },
                                   "land": {
                                     "$ref": "#/$defs/LandTYPE"
                                   }
                                 },
                                 "required": []
                               }
                             }
                           }
                    
                    """);

            /* Get the template (uses cache internally) */
            Template template = freemarkerConf.getTemplate("mermaid.ftlh");

            /* Merge data-model with template */
            JsonObject model = JsonObject.EMPTY.put("classDiagram", schema.as(MermaidClassDiagram.class).toString());
            template.process(model.asMapsAndLists(), response.getWriter());
            // Note: Depending on what `out` is, you may need to call `out.close()`.
            // This is usually the case for file output, but not for servlet output.

        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(500);
        }
        return response;
    }
}
