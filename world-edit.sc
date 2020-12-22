//World edit

global_lang_ids = ['en_us','it_it'];//defining up here for command to work

//# New commands format:
//#   [command_for_carpet, interpretation_for_carpet, false] (will hide it from help menu)
//#   [command_for_carpet, interpretation_for_carpet, [optional_arguments_since, description, description_tooltip, description_action]]
//# optional_arguments_since is the position of the first arg to make optional (<arg> to [arg]). If none, use -1
//# 
//# Suggestion is derived from command_for_carpet, everything before the first `<`.
//# Command prefix (/world-edit ) is automatically added to description_action
//# description_action accepts both execute and suggest actions, by prefixing it with either `!` or `?` (needed)
//# Both description and description_tooltip must be a language string, or a lambda if string needs args.
//# Try to fit each entry in a single line (help menu) for proper pagination (until something is done).
base_commands_map = [
    ['', _()->_help(1), false],
    ['help', _()->_help(1), false],
    ['help <page>', '_help', [0, 'help_cmd_help', null, null]],
    ['lang <lang>', _(lang)->(global_lang=lang), [-1, 'help_cmd_lang', _()->_translate('help_cmd_lang_tooltip',global_lang_ids), null]],
    ['fill <block>', ['fill',null], false],
    ['fill <block> <replacement>', 'fill', [1, 'help_cmd_fill', 'help_cmd_fill_tooltip', null]],
    ['undo', ['undo', 1], false],
    ['undo <moves>', 'undo', [0, 'help_cmd_undo', null, null]],
    ['undo all', ['undo', 0], [-1, 'help_cmd_undo_all', null, null]],
    ['undo history', 'print_history', [-1, 'help_cmd_undo_history', null, null]],
    ['redo', ['redo', 1], false],
    ['redo <moves>', 'redo', [0, 'help_cmd_redo', 'help_cmd_redo_tooltip', null]],
    ['redo all', ['redo', 0], [-1, 'help_cmd_redo_all', null, null]],
    ['wand', '_set_or_give_wand', [-1, 'help_cmd_wand', null, null]],
    ['wand <wand>', _(wand)->(global_wand=wand:0), [-1, 'help_cmd_wand_2', null, null]],
    ['rotate <pos> <degrees> <axis>', 'rotate', [-1, 'help_cmd_rotate', 'help_cmd_rotate_tooltip', null]],//will replace old stuff if need be
    ['stack', ['stack',1,null], false],
    ['stack <count>', ['stack',null], false],
    ['stack <count> <direction>', 'stack', [0, 'help_cmd_stack', 'help_cmd_stack_tooltip', null]],
    ['expand <pos> <magnitude>', 'expand', [-1, 'help_cmd_expand', 'help_cmd_expand_tooltip', null]],
    ['clone <pos>', ['clone',false], [-1, 'help_cmd_clone', null, null]],
    ['move <pos>', ['clone',true], [-1, 'help_cmd_move', null, null]],
    ['selection clear', 'clear_selection', false], //TODO help for this and below
    ['selection expand', _()->selection_expand(1), false],
    ['selection expand <amount>', 'selection_expand', false],
    ['selection move', _() -> selection_move(1, null), false],
    ['selection move <amount>', _(n)->selection_move(n, null), false],
    ['selection move <amount> <direction>', 'selection_move',false],
    ['settings quick_select <bool>', _(b) -> global_quick_select = b, false]
];

// Proccess commands map for Carpet
global_commands_map = {};
for(base_commands_map,
    global_commands_map:(_:0) = _:1;
);
// Proccess commands map for help
global_help_commands = [];
for(base_commands_map,
    if(_:2, //Check it's not skipped (aka false)
        visible_command = _:0;
        suggestion = reduce(split(visible_command),if((_ == '<'),break(),_a+_),'');
        if((search_pos = _:2:0) != -1, // Proccess arguments
            current_pos = [-1, -1];
            visible_command = reduce(map(split(visible_command),
                if(_ == '<', current_pos:0 += 1; if(current_pos:0 >= search_pos, '[', _)
                  ,_ == '>', current_pos:1 += 1; if(current_pos:1 >= search_pos, ']', _) ,_ ) ),
                  _a+_,'');
        );
        global_help_commands += ['g - '+visible_command, suggestion, _:2:1, _:2:2, _:2:3];
    )
);


__config()->{
    'commands'-> global_commands_map,
    'arguments'->{
        'replacement'->{'type'->'blockpredicate'},
        'moves'->{'type'->'int','min'->1,'suggest'->[]},//todo decide on whether or not to add max undo limit
        'degrees'->{'type'->'int','suggest'->[]},
        'axis'->{'type'->'term','options'->['x','y','z']},
        'wand'->{'type'->'item','suggest'->['wooden_sword','wooden_axe']},
        'direction'->{'type'->'term','options'->['north','south','east','west','up','down']},
        'count'->{'type'->'int','min'->1,'suggest'->[]},
        'flags'->{'type'->'text'},
        'amount'->{'type'->'int'},
        'magnitude'->{'type'->'float','suggest'->[1,2,0.5]},
        'lang'->{'type'->'term','options'->global_lang_ids},
        'page'->{'type'->'int','min'->1,'suggest'->[1,2,3]},
    }
};
//player globals

global_wand = 'wooden_sword';
global_history = [];
global_undo_history = [];
global_quick_select = true;


//Extra boilerplate

global_affected_blocks=[];

//Block-selection

global_selection = {}; // holds ids of two official corners of the selection
global_markers = {};

_help(page) ->
(
    command = '/'+system_info('app_name')+' ';
    // Header
    print(format(_translate('help_header_prefix'), _translate('help_header_title'), _translate('help_header_suffix')));
    
    // Help entries are generated on top, in the commands map. Check docs there
    // Hardcoded help entries (info)
    // Syntax: [pre-arrow, pre-arrow command to suggest, post-arrow text, post-arrow tooltip (lang string/lambda), post-arrow action]
    help_entries = [
        [null, null, 'help_welcome', 'help_welcome_tooltip', null],
        [if(length(global_selection) > 1,_translate('help_your_selection')), '', if(length(global_selection) > 1,
                            _()->_translate('help_selection_bounds', global_selection:0, global_selection:1)
                            ,'help_make_selection'), null, '?wand '],
        [_translate('help_selected_wand'), 'wand ', _()->_translate('help_selected_wand_item',title(replace(global_wand, '_', ' '))), 'help_sel_wand_tooltip', '?wand '],
        [_translate('help_app_lang'), 'lang ', _()->_translate('help_app_lang_selected', global_lang), 'help_app_lang_tooltip', '?lang '],
        [_translate('help_list_title'), 'help', null, null]
    ];
    
    for(global_help_commands,
        help_entries += _;
    );
    remaining_to_display = 8;
    current_entry = ((page-1)*8);
    entry_number = length(help_entries);
    while(remaining_to_display != 0 && current_entry < entry_number, entry_number,
        entry = help_entries:current_entry;
        //Allow different desc actions
        if(slice(entry:4,0,1) == '!',
            description_action = '!'+command+slice(entry:4,1);
            print('Hello');
        , slice(entry:4,0,1) == '?',
            description_action = '?'+command+slice(entry:4,1);
        );
        if(entry:2 != null, arrow = 'd  -> ');
        //print(entry:2);
        print(format(entry:0, '?'+command+entry:1, arrow, 
            if(type(entry:2) == 'function', call(entry:2), _translate(entry:2)), 
            '^'+if(type(entry:3) == 'function', call(entry:3), _translate(entry:3)),description_action)
        );

        current_entry += 1;
        remaining_to_display += -1;
        description_action = arrow = null;
    );
    
    // Footer
    footer = format('');
    if (page < 10, // In case we reach this, make it still be centered
        footer += ' ';
    );
    footer += format(_translate('help_pagination_prefix'));
    if (page != 1,
        footer += format('d [<<]',
                    '^'+_translate('help_pagination_first'),
                    '!'+command+'help');
        footer += ' ';
        footer += format('d [<]',
                    '^'+_translate('help_pagination_prev',page-1),
                    '!'+command+'help '+(page-1));
    ,
        footer += format('g [<<]');
        footer += ' ';
        footer += format('g [<]');
    );
    footer += ' ';
    footer += format(_translate('help_pagination_page',page));
    if ((8*page) < entry_number,
        last_page = ceil((entry_number)/8);
        footer += format('d [>]',
                        '^'+_translate('help_pagination_next',page+1),
                        '!'+command+'help '+(page+1));
        footer += ' ';
        footer += format('d [>>]',
                        '^'+_translate('help_pagination_last',last_page),
                        '!'+command+'help '+last_page);
    ,
        footer += format('g [>]');
        footer += ' ';
        footer += format('g [>>]');
    );
    footer += format(_translate('help_pagination_suffix'));
    print(footer);
);

clear_markers(... ids) ->
(
    if (!ids, ids = keys(global_markers));
    for (ids,
        (e = entity_id(_)) && modify(e, 'remove');
        delete(global_markers:_);
    )
);

_create_marker(pos, block) ->
(
    marker = create_marker(null, pos+0.5, block, false);
    modify(marker, 'effect', 'glowing', 72000, 0, false, false);
    modify(marker, 'fire', 32767);
    id = marker ~ 'id';
    global_markers:id = {'pos' -> pos, 'id' -> id};
    id;
);

_get_marker_position(marker_id) -> global_markers:marker_id:'pos';

clear_selection() ->
(
    for(values(global_selection), clear_markers(_));
    global_selection = {};
);

selection_move(amount, direction) ->
(
    [from, to] = _get_current_selection();
    point1 = _get_marker_position(global_selection:'from');
    point2 = _get_marker_position(global_selection:'to');
    p = player();
    if (p == null && direction == null, exit(_translate('move_selection_no_player_error')));
    translation_vector = if(direction == null, get_look_direction(p)*amount, pos_offset([0,0,0],direction, amount));
    clear_markers(global_selection:'from', global_selection:'to');
    point1 = point1 + translation_vector;
    point2 = point2 + translation_vector;
    global_selection = {
        'from' -> _create_marker(point1, 'lime_concrete'),
        'to' -> _create_marker(point2, 'blue_concrete')
    };
);

selection_expand(amount) ->
(
    [from, to] = _get_current_selection();
    point1 = _get_marker_position(global_selection:'from');
    point2 = _get_marker_position(global_selection:'to');
    for (range(3),
        size = to:_-from:_+1;
        c_amount = if (size >= -amount, amount, floor(size/2));
        if (point1:_ > point2:_, c_amount = - c_amount);
        point1:_ += -c_amount;
        point2:_ +=  c_amount;
    );
    clear_markers(global_selection:'from', global_selection:'to');
    global_selection = {
        'from' -> _create_marker(point1, 'lime_concrete'),
        'to' -> _create_marker(point2, 'blue_concrete')
    };
);

__on_player_swings_hand(player, hand) ->
(
    if(player~'holds':0==global_wand,
        if (length(global_selection)<2,
            // finish selection
            if (!global_selection, _set_start_point(player), _set_end_point(player) );
        ,
            // selection is already made
            if (global_quick_select,
                clear_selection();
                _set_start_point(player)
            )
        )
    )
);

_set_start_point(player) ->
(
    clear_markers();
    start_pos = _get_player_look_at_block(player, 4.5);
    marker = _create_marker(start_pos, 'lime_concrete');
    global_selection = {'from' -> marker};
    if (!global_rendering, _render_selection_tick(player~'name'));
);

_set_end_point(player) ->
(
    end_pos = _get_player_look_at_block(player, 4.5);
    marker = _create_marker(end_pos, 'blue_concrete');
    global_selection:'to' = marker;
);

global_rendering = false;
_render_selection_tick(player_name) ->
(
    if (!global_selection,
        global_rendering = false;
        clear_markers(); // remove all selections, points etc. // debatable
        return()
    );
    global_rendering = true;
    p = player(player_name);
    active = (length(global_selection) == 1);

    start_marker = global_selection:'from';
    start = if(
        start_marker,  _get_marker_position(start_marker),
        p,             _get_player_look_at_block(p, 4.5)
    );

    end_marker = global_selection:'to';
    end = if(
        end_marker,              _get_marker_position(end_marker),
        p, _get_player_look_at_block(p, 4.5)
    );

    if (start && end,
        zipped = map(start, [_, end:_i]);
        from = map(zipped, min(_));
        to = map(zipped, max(_));
        draw_shape('box', if(active, 1, 40), 'from', from, 'to', to+1, 'line', 3, 'color', if(active, 0x00ffffff, 0xAAAAAAff));
        if (!end_marker,   draw_shape('box', 1, 'from', end, 'to', end+1, 'line', 1, 'color', 0x0000FFFF, 'fill', 0x0000FF55 ));
        if (!start_marker, draw_shape('box', 1, 'from', start, 'to', start+1, 'line', 1, 'color', 0xbfff00FF, 'fill', 0xbfff0055 ));
    );
    schedule(if(active, 1, 20), '_render_selection_tick', player_name);
);

_get_player_look_at_block(player, range) ->
(
    block = query(player, 'trace', range, 'blocks');
    if (block,
        pos(block)
    ,
        map(pos(player)+player~'look'*range+[0, player~'eye_height', 0], floor(_));
    )
);

_get_current_selection()->
(
    if( length(global_selection) < 2,
        exit(_translate('no_selection_error', []))
    );
    start = _get_marker_position(global_selection:'from');
    end = _get_marker_position(global_selection:'to');
    zipped = map(start, [_, end:_i]);
    [map(zipped, min(_)), map(zipped, max(_))]
);

//Misc functions

_set_or_give_wand() -> (
    p = player;
    //give player wand if hand is empty
    if(held_item_tuple = p~'holds' == null, 
       slot = invenroty_set(p, p~'selected_slot', 1, global_wand);
       return()
    );
    //else, set current held item as wand, if valid
    held_item = held_item_tuple:0;
    if( (['tools', 'weapons']~item_category(held_item)) != null,
        global_wand = held_item;
        print(p, str('%s is now the app\'s wand, use it with care.', held_item)),
       //else, can't set as wand
       print(p, 'Wand has to be a tool or weapon')
    )
);

//Config Parser

_parse_config(config) -> (
    if(type(config) != 'list', config = [config]);
    ret = {};
    for(config,
        if(_ ~ '^\\w+ ?= *.+$' != null,
            key = _ ~ '^\\w+(?= ?= *.+)';
            value = _ ~ ('(?<='+key+' ?= ?) *([^ ].*)');
            ret:key = value
        )
    );
    ret
);

//Translations

global_lang=null;//default en_us
global_langs = {};
for(global_lang_ids,
    global_langs:_ = read_file(_, 'text');
    if(global_langs:_ == null,
        write_file(_, 'text', global_langs:_ = [
            'language_code =    en_us',
            'language =         english',

            
            'help_header_prefix =      c ----------------- [ ',
            'help_header_title =       d World-Edit Help',
            'help_header_suffix =      c  ] -----------------',
            'help_welcome =            c Welcome to the World-Edit Scarpet app\'s help!',
            'help_welcome_tooltip =    y Hooray!',
            'help_your_selection =     c Your selection',
            'help_selection_bounds =   l %s to %s',
            'help_make_selection =     c Left click your wand at start and final position to select',
            'help_selected_wand =      c Selected wand', //Could probably be used in more places
            'help_selected_wand_item = l %s',
            'help_sel_wand_tooltip =   g Use the wand command to change',
            'help_app_lang =           c App Language ', //Could probably be used in more places
            'help_app_lang_selected =  l %s',
            'help_app_lang_tooltip =   g Use the lang command to change it',
            'help_list_title =         y Command list (without prefix):',
            'help_pagination_prefix =  c --------------- ',
            'help_pagination_page =    y Page %d ',
            'help_pagination_suffix =  c  ---------------',
            'help_pagination_first =   g Go to first page',
            'help_pagination_prev =    g Go to previous page (%d)',
            'help_pagination_next =    g Go to next page (%d)',
            'help_pagination_last =    g Go to last page (%d)',
            'help_cmd_help =           l Shows this help menu, or a specified page',
            'help_cmd_lang =           l Changes current app\'s language to <lang>',
            'help_cmd_lang_tooltip =   g Available languages are %s',
            'help_cmd_fill =           l Fills the selection, filterable',
            'help_cmd_fill_tooltip =   g You can use a tag in the replacement argument',
            'help_cmd_undo =           l Undoes last n moves, one by default',
            'help_cmd_undo_all =       l Undoes the entire action history',
            'help_cmd_undo_history =   l Shows the history of undone actions',
            'help_cmd_redo =           l Redoes last n undoes, one by default',
            'help_cmd_redo_tooltip =   g Also shows up in undo history',
            'help_cmd_redo_all =       l Redoes the entire undo history',
            'help_cmd_wand =           l Sets held item as wand or gives it if hand is empty',
            'help_cmd_wand_2 =         l Changes the current wand item',
            'help_cmd_rotate =         l Rotates [deg] about [pos]',
            'help_cmd_rotate_tooltip = g Axis must be x, y or z',
            'help_cmd_stack =          l Stacks selection n times in dir',
            'help_cmd_stack_tooltip =  g If not provided, direction is player\s view direction by default',
            'help_cmd_expand =         l Expands sel [magn] from pos', //This is not understandable
            'help_cmd_expand_tooltip = g Expands the selection [magnitude] from [pos]',
            'help_cmd_clone =          l Clones selection to <pos>',
            'help_cmd_move =           l Moves selection to <pos>',

            'filled =                  gi Filled %d blocks',                                    // blocks number
            'no_undo_history =         w No undo history to show for player %s',                // player
            'many_undo =               w Undo history for player %s is very long, showing only the last ten items', // player
            'entry_undo =              w %d: type: %s\\n    affected positions: %s',             // index, command type, blocks number
            'no_undo =                 r No actions to undo for player %s',                     // player
            'more_moves_undo =         w Your number is too high, undoing all moves for %s',     // player
            'success_undo =            gi Successfully undid %d operations, filling %d blocks', // moves number, blocks number

            'move_selection_no_player_error = To move selection in the direction of the player, you need to have a player',
            'no_selection_error =             Missing selection for operation',
        ])
    );
    global_langs:_ = _parse_config(global_langs:_)
);
_translate_internal(key, replace_list) -> (
    // print(player(),key+' '+replace_list);
    lang_id = global_lang;
    if(lang_id == null || !has(global_langs, lang_id),
        lang_id = global_lang_ids:0);
    if(key == null,
        null,
    ,
        str(global_langs:lang_id:key, replace_list)
    )
);
_print(player, key, ... replace) -> print(player, format(_translate_internal(key, replace)));
_translate(key, ... replace) -> _translate_internal(key, replace);


//Command processing functions

set_block(pos,block,replacement)->(//use this function to set blocks
    success=null;
    existing = block(pos);
    if(block != existing && (!replacement || _block_matches(existing, replacement)),
        postblock=set(existing,block);
        success=existing;
        global_affected_blocks+=[pos,postblock,existing];//todo decide whether to replace all blocks or only blocks that were there before action (currently these are stored, but that may change if we dont want them to)
    );
    bool(success)//cos undo uses this
);

_block_matches(existing, block_predicate) ->
(
    [name, block_tag, properties, nbt] = block_predicate;

    (name == null || name == existing) &&
    (block_tag == null || block_tags(existing, block_tag)) &&
    all(properties, block_state(existing, _) == properties:_) &&
    (!tag || tag_matches(block_data(existing), tag))
);

add_to_history(function,player)->(

    if(length(global_affected_blocks)==0,return());//not gonna add empty list to undo ofc...
    command={
        'type'->function,
        'affected_positions'->global_affected_blocks
    };

    _print(player,'filled',length(global_affected_blocks));
    global_affected_blocks=[];
    global_history+=command;
);

print_history()->(
    player=player();
    history = global_history;
    if(length(history)==0||history==null,_print(player, 'no_undo_history', player));
    if(length(history)>10,_print(player, 'many_undo', player));
    total=min(length(history),10);//total items to print
    for(range(total),
        command=history:(length(history)-(_+1));//getting last 10 items in reverse order
        _print(player, 'entry_undo', history~command+1,command:'type', length(command:'affected_positions'))
    )
);

//Command functions

undo(moves)->(
    player = player();
    history=global_history;
    if(length(history)==0||history==null,exit(_print(player, 'no_undo', player)));//incase an op was running command, we want to print error to them
    if(length(history)<moves,_print(player, 'more_moves_undo', player);moves=0);
    if(moves==0,moves=length(history));
    for(range(moves),
        command = history:(length(history)-1);//to get last item of list properly

        for(command:'affected_positions',
            set_block(_:0,_:2,null)//todo decide whether to replace all blocks or only blocks that were there before action (currently these are stored, but that may change if we dont want them to)
        );

        delete(history,(length(history)-1))
    );
    global_undo_history+=global_affected_blocks;//we already know that its not gonna be empty before this, so no need to check now.
    print(player,format('gi Successfully undid '+moves+' operations, filling '+length(global_affected_blocks)+' blocks'));
    global_affected_blocks=[];
);

redo(moves)->(
    player=player();
    history=global_undo_history;
    if(length(history)==0||history==null,exit(print(player,format('r No actions to redo for player '+player))));
    if(length(history)<moves,print(player,'Too many moves to redo, redoing all moves for '+player);moves=0);
    if(moves==0,moves=length(history));
    for(range(moves),
        command = history:(length(history)-1);//to get last item of list properly

        for(command,
            set_block(_:0,_:2,null);
        );

        delete(history,(length(history)-1))
    );
    global_history+={'type'->'redo','affected_positions'->global_affected_blocks};//Doing this the hacky way so I can add custom goodbye message
    print(player,format('gi Successfully redid '+moves+' operations, filling '+length(global_affected_blocks)+' blocks'));
    global_affected_blocks=[];
    _print(player, 'success_undo', moves, affected);
);

fill(block,replacement)->(
    player=player();
    [pos1,pos2]=_get_current_selection();
    volume(pos1,pos2,set_block(pos(_),block,replacement));

    add_to_history('fill', player)
);

rotate(centre, degrees, axis)->(
    player=player();
    [pos1,pos2]=_get_current_selection();

    rotation_map={};
    rotation_matrix=[];
    if( axis=='x',
        rotation_matrix=[
            [1,0,0],
            [0,cos(degrees),-sin(degrees)],
            [0,sin(degrees),cos(degrees)]
        ],
        axis=='y',
        rotation_matrix=[
            [cos(degrees),0,sin(degrees)],
            [0,1,0],
            [-sin(degrees),0,cos(degrees)]
        ],//axis=='z'
        rotation_matrix=[
            [cos(degrees),-sin(degrees),0],
            [sin(degrees),cos(degrees),0]
            [0,0,1],
        ]
    );

    volume(pos1,pos2,
        block=block(_);//todo rotating stairs etc.
        prev_pos=pos(_);
        subtracted_pos=prev_pos-centre;
        rotated_matrix=rotation_matrix*[subtracted_pos,subtracted_pos,subtracted_pos];//cos matrix multiplication dont work in scarpet, yet...
        new_pos=[];
        for(rotated_matrix,
            new_pos+=reduce(_,_a+_,0)
        );
        new_pos=new_pos+centre;
        put(rotation_map,new_pos,block)//not setting now cos still querying, could mess up and set block we wanted to query
    );

    for(rotation_map,
        set_block(_,rotation_map:_,null)
    );

    add_to_history('rotate', player)
);

clone(new_pos, move)->(
    player=player();
    [pos1,pos2]=_get_current_selection();

    min_pos=map(pos1,min(_,pos2:_i));
    clone_map={};
    translation_vector=new_pos-min_pos;

    volume(pos1,pos2,
        put(clone_map,pos(_)+translation_vector,block(_));//not setting now cos still querying, could mess up and set block we wanted to query
        if(move,set_block(pos(_),if(has(block_state(_),'waterlogged'),'water','air')),null)//check for waterlog
    );

    for(clone_map,
        set_block(_,clone_map:_,null)
    );

    add_to_history(if(move,'move','clone'), player)
);

stack(count,direction) -> (
    player=player();
    translation_vector = pos_offset([0,0,0],if(direction,direction,player~'facing'));
    [pos1,pos2]=_get_current_selection();

    translation_vector = translation_vector*map(pos1-pos2,abs(_)+1);

    loop(count,
        c = _;
        offset = translation_vector*(c+1);
        volume(pos1,pos2,
            set_block(pos(_)+offset,_,null);
        );
    );

    add_to_history('stack', player)
);


expand(centre, magnitude)->(
    player=player();
    [pos1,pos2]=_get_current_selection();
    expand_map={};
    min_pos=map(pos1,min(_,pos2:_i));
    max_pos=map(pos1,max(_,pos2:_i));


    step=max(1,magnitude)-1;
    volume(pos1,pos2,
        if(block(_)!='air',//cos that way shrinkage retains more blocks and less garbage
            put(expand_map,(pos(_)-centre)*(magnitude-1)+pos(_),block(_))
        )
    );

    for(expand_map,
        set_block(_,expand_map:_,null)
    );
    add_to_history('expand',player)
);
