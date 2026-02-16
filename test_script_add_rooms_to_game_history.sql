WITH expanded AS (
    SELECT
        user_1 AS user_id,
        game_type,
        created_at,
        id AS room_id,
        user_1_side AS user_side,
        winner_side
    FROM
        chess_rooms_2

    UNION ALL

    SELECT
        user_2 AS user_id,
        game_type,
        created_at,
        id AS room_id,
        user_2_side AS user_side,
        winner_side
    FROM
        chess_rooms_2
),
     numbered AS (
         SELECT
             user_id,
             game_type,
             room_id,
             user_side,
             winner_side,
             created_at,
             row_number() OVER (PARTITION BY user_id ORDER BY created_at) AS match_number
         FROM
             expanded
     ),
     final_data AS (
         SELECT
             user_id,
             game_type,
             match_number,
             room_id,
             created_at,
             CASE
                 WHEN winner_side IS NULL THEN 'DRAW'
                 WHEN user_side = winner_side THEN 'WIN'
                 ELSE 'LOSE'
                 END AS user_game_status,
             false AS favourite
         FROM
             numbered
     )
INSERT INTO game_history_1 (user_id,  room_id, game_type, match_number,user_game_status, favourite, created_at)
SELECT
    user_id,
    room_id,
    game_type,
    match_number,
    user_game_status,
    favourite,
    created_at
FROM
    final_data
    ON CONFLICT (user_id, room_id) DO nothing
;