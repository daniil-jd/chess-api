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
             CASE
                 WHEN winner_side IS NULL THEN 'DRAW'
                 WHEN user_side = winner_side THEN 'WIN'
                 ELSE 'LOSE'
                 END AS user_game_status,
             false AS favourite
         FROM
             numbered
     )
INSERT INTO game_history (user_id, game_type, match_number, room_id, user_game_status, favourite)
SELECT
    user_id,
    game_type,
    match_number,
    room_id,
    user_game_status,
    favourite
FROM
    final_data
    ON CONFLICT (user_id, game_type, match_number) DO nothing
;