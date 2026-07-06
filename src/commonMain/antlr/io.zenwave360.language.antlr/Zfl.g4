grammar Zfl;

@parser::members {
    override fun match(ttype: Int): org.antlr.v4.kotlinruntime.Token {
        try { // hack to not parse suffix javadoc after new lines (hidden tokens)
            if(context is Suffix_javadocContext) {
                val currentTokenIndex = currentToken!!.tokenIndex;
                val prevToken = tokenStream.get(currentTokenIndex - 1); // getTokenStream().get(currentTokenIndex - 1);
                if(prevToken?.text?.contains("\n") == true) {
                    // println("RULE_suffix_javadoc")
                    val t = currentToken!!;
                    return t;
                }
            }
        } catch (e: Exception) {
            e.printStackTrace();
        }
        return super.match(ttype);
    }
} // end members

LPAREN: '(';
RPAREN: ')';
LBRACE: '{';
RBRACE: '}';
LBRACK: '[';
RBRACK: ']';
OR: '|';
COMMA: ',';
COLON: ':';
TRUE: 'true';
FALSE: 'false';
NULL: 'null';
EQUALS: '=';
ARRAY: '[]';
OPTIONAL: '?';
DOT: '.';
SLASH: '/';

// Keywords
IMPORT: 'import';
CONFIG: 'config';
// Flow Keywords
FLOW: 'flow';
SYSTEMS: 'systems';
ZDL: 'zdl';
SERVICE: 'service';
COMMANDS: 'commands';
EVENTS: 'events';
START: 'start';
WHEN: 'when';
DO: 'do';
CALL: 'call';
ASYNC: 'async';
ON: 'on';
EMITS: 'emits';
RESPONSE: 'response';
FOR: 'for';
END: 'end';
COMPLETED: 'completed';
SUSPENDED: 'suspended';
CANCELLED: 'cancelled';
AND: 'and';

// field validators
REQUIRED: 'required';
UNIQUE: 'unique';
MIN: 'min';
MAX: 'max';
MINLENGTH: 'minlength';
MAXLENGTH: 'maxlength';
EMAIL: 'email';
PATTERN: 'pattern';
OPTION_NAME: '@' [a-zA-Z_][a-zA-Z0-9_]*;

fragment DIGIT : [0-9] ;
ID: [a-zA-Z_][a-zA-Z0-9_]*;
POLICY_ID: [a-zA-Z_][a-zA-Z0-9_-]*;
INT: DIGIT+ ;
NUMBER: DIGIT+ ([.] DIGIT+)? ;

// Comments
//SUFFIX_JAVADOC: {getCharPositionInLine() > 10}? '/**' .*? '*/';
//SUFFIX_JAVADOC: '/***' .*? '*/';
JAVADOC: '/**' .*? '*/';
LINE_COMMENT : '//' .*? '\r'? '\n' -> channel(HIDDEN) ; // Match "//" stuff '\n'
COMMENT : '/*' .*? '*/' -> channel(HIDDEN) ; // Match "/*" stuff "*/"

DOUBLE_QUOTED_STRING :  '"' (ESC | ~["\\])* '"' ;
SINGLE_QUOTED_STRING :  '\'' (ESC | ~['\\])* '\'' ;
fragment ESC :   '\\' ['"\\/bfnrt] ;

// Whitespace
WS: [ \t\r\n]+ -> channel(HIDDEN);

/** "catch all" rule for any char not matche in a token rule of your
 *  grammar. Lexers in Intellij must return all tokens good and bad.
 *  There must be a token to cover all characters, which makes sense, for
 *  an IDE. The parser however should not see these bad tokens because
 *  it just confuses the issue. Hence, the hidden channel.
 */
ERRCHAR: . -> channel(HIDDEN);

// Rules
zfl: import_* config? systems? flow* EOF;

import_: '@import' LPAREN (import_value | import_key COLON import_value) RPAREN;
import_key: ID;
import_value: string;
global_javadoc: JAVADOC;
javadoc: JAVADOC;
suffix_javadoc: JAVADOC;

// values
keyword: ID | IMPORT | CONFIG | FLOW | SYSTEMS | ZDL | SERVICE | COMMANDS | EVENTS | START | WHEN | DO | FOR | END | COMPLETED | SUSPENDED | CANCELLED | AND | ASYNC | RESPONSE | REQUIRED | UNIQUE | MIN | MAX | MINLENGTH | MAXLENGTH | EMAIL | PATTERN;

//complex_value: value | array | object;
//value: simple | object;
//string: keyword | SINGLE_QUOTED_STRING | DOUBLE_QUOTED_STRING;
//simple: keyword | SINGLE_QUOTED_STRING | DOUBLE_QUOTED_STRING | INT | NUMBER | TRUE | FALSE | NULL;
//pair: keyword COLON value;
//object: LBRACE pair (COMMA pair)* RBRACE;
//array: LBRACK? value (COMMA value)* RBRACK?;

complex_value : value | array_plain | pairs;
value: object| array | simple;
string: keyword | SINGLE_QUOTED_STRING | DOUBLE_QUOTED_STRING;
simple: ID | SINGLE_QUOTED_STRING | DOUBLE_QUOTED_STRING | INT | NUMBER | TRUE | FALSE | NULL | keyword;
object: LBRACE pair (COMMA pair)* RBRACE
      | LBRACE RBRACE;
pair: string COLON value;
array: LBRACK value (COMMA value)* RBRACK
     | LBRACK RBRACK;
array_plain: simple (COMMA simple)*;
pairs: pair (COMMA pair)*;

fields: (field COMMA?)*;
field: javadoc? annotations field_name field_type suffix_javadoc?;
field_name: keyword;
field_type: ID | ID ARRAY;

config: global_javadoc? CONFIG config_body;
config_body: LBRACE config_option* RBRACE;
config_option: field_name complex_value;

// @options
annotations: option*;
option: option_name (LPAREN option_value RPAREN)?; // (LPAREN option_value RPAREN)? | '@' option_name (LPAREN option_value RPAREN)?;
option_name: OPTION_NAME;
option_value: complex_value;

// systems block
systems: javadoc? annotations SYSTEMS LBRACE system* RBRACE;
system: javadoc? annotations system_name LBRACE system_body RBRACE;
system_name: ID;
system_body: system_services;
system_services: system_service*;
system_service: javadoc? annotations SERVICE system_service_name (FOR LPAREN system_service_aggregates RPAREN)? (LBRACE system_service_body RBRACE)?;
system_service_name: ID;
system_service_aggregates: ID (COMMA ID)*;
system_service_body: COMMANDS COLON system_service_command_list;
system_service_command_list: ID (COMMA ID)*;

// flows
flow: javadoc? annotations FLOW flow_name LBRACE flow_body RBRACE;
flow_name: ID;
flow_body: (flow_start | flow_when | flow_do | flow_end)*;

// start events
flow_start: javadoc? annotations START flow_start_name LBRACE fields RBRACE;
flow_start_name: ID;

// when blocks
flow_when: javadoc? annotations WHEN flow_when_trigger DO flow_command_name (LBRACE flow_do_body RBRACE)?;
flow_when_trigger: flow_when_trigger_group (AND flow_when_trigger_group)*;
flow_when_trigger_group: flow_when_event_trigger ((COMMA | OR) flow_when_event_trigger)* (COMMA | OR)?;
flow_when_event_trigger: ID;
flow_command_name: ID;

// action blocks
flow_do: javadoc? annotations DO flow_command_name LBRACE flow_do_body RBRACE;
flow_do_body: flow_do_statement*;
flow_do_statement: flow_do_service | flow_do_call | flow_do_on | flow_do_signal;
flow_do_service: SERVICE flow_service_path;
flow_do_call: ASYNC? CALL flow_command_name;
flow_do_on: annotations ON flow_event_name (CALL flow_command_name | flow_signal_body);
flow_do_signal: annotations flow_signal_body;
flow_signal_body: ((EMITS RESPONSE?) | RESPONSE) flow_event_list;
flow_event_list: flow_event_name (COMMA flow_event_name)*;
flow_service_path: flow_service_segment ((DOT | SLASH) flow_service_segment)*;
flow_service_segment: ID;
flow_event_name: ID;

// end block
flow_end: javadoc? annotations END LBRACE flow_end_outcomes RBRACE;
flow_end_outcomes: flow_end_outcome+;
flow_end_outcome: flow_end_outcome_name COLON flow_end_outcome_list;
flow_end_outcome_name: keyword;
flow_end_outcome_list: flow_end_outcome_event (COMMA flow_end_outcome_event)*;
flow_end_outcome_event: ID;
