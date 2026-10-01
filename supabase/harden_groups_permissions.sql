-- Apply after fresh_database.sql has run.
-- This is a non-destructive privilege/policy repair for the already-created schema.
-- Keep `private` out of Supabase's exposed Data API schemas (default: public, graphql_public).

BEGIN;

CREATE SCHEMA IF NOT EXISTS private;
REVOKE ALL ON SCHEMA private FROM PUBLIC, anon;
GRANT USAGE ON SCHEMA private TO authenticated;

CREATE OR REPLACE FUNCTION private.is_group_member(p_group_id UUID)
RETURNS BOOLEAN
LANGUAGE SQL
STABLE
SECURITY DEFINER
SET search_path = pg_catalog, public, auth
AS $$
    SELECT auth.uid() IS NOT NULL AND EXISTS (
        SELECT 1 FROM public.group_members AS gm
        WHERE gm.group_id = p_group_id AND gm.user_id = auth.uid()
    );
$$;

CREATE OR REPLACE FUNCTION private.is_group_owner(p_group_id UUID)
RETURNS BOOLEAN
LANGUAGE SQL
STABLE
SECURITY DEFINER
SET search_path = pg_catalog, public, auth
AS $$
    SELECT auth.uid() IS NOT NULL AND EXISTS (
        SELECT 1 FROM public.groups AS g
        WHERE g.id = p_group_id AND g.owner_id = auth.uid()
    );
$$;

CREATE OR REPLACE FUNCTION private.is_group_admin(p_group_id UUID)
RETURNS BOOLEAN
LANGUAGE SQL
STABLE
SECURITY DEFINER
SET search_path = pg_catalog, public, auth
AS $$
    SELECT private.is_group_owner(p_group_id) OR EXISTS (
        SELECT 1 FROM public.group_members AS gm
        WHERE gm.group_id = p_group_id
          AND gm.user_id = auth.uid()
          AND gm.role = 'admin'
    );
$$;

CREATE OR REPLACE FUNCTION private.is_group_moderator(p_group_id UUID)
RETURNS BOOLEAN
LANGUAGE SQL
STABLE
SECURITY DEFINER
SET search_path = pg_catalog, public, auth
AS $$
    SELECT private.is_group_admin(p_group_id) OR EXISTS (
        SELECT 1 FROM public.group_members AS gm
        WHERE gm.group_id = p_group_id
          AND gm.user_id = auth.uid()
          AND gm.role = 'moderator'
    );
$$;

CREATE OR REPLACE FUNCTION private.can_read_group(p_group_id UUID)
RETURNS BOOLEAN
LANGUAGE SQL
STABLE
SECURITY DEFINER
SET search_path = pg_catalog, public, auth
AS $$
    SELECT auth.uid() IS NOT NULL AND EXISTS (
        SELECT 1 FROM public.groups AS g
        WHERE g.id = p_group_id
          AND (
              (g.status = 'active' AND g.visibility = 'public')
              OR g.owner_id = auth.uid()
              OR private.is_group_member(g.id)
          )
    );
$$;

REVOKE ALL ON FUNCTION private.is_group_member(UUID) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION private.is_group_owner(UUID) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION private.is_group_admin(UUID) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION private.is_group_moderator(UUID) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION private.can_read_group(UUID) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION private.is_group_member(UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION private.is_group_owner(UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION private.is_group_admin(UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION private.is_group_moderator(UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION private.can_read_group(UUID) TO authenticated;

-- Move policy checks off public RPC endpoints. The policy names and access rules stay unchanged.
DO $$
DECLARE
    policy_row RECORD;
    using_expression TEXT;
    check_expression TEXT;
BEGIN
    FOR policy_row IN
        SELECT
            n.nspname AS schema_name,
            c.relname AS table_name,
            p.polname AS policy_name,
            pg_get_expr(p.polqual, p.polrelid) AS using_expression,
            pg_get_expr(p.polwithcheck, p.polrelid) AS check_expression
        FROM pg_policy AS p
        JOIN pg_class AS c ON c.oid = p.polrelid
        JOIN pg_namespace AS n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public'
          AND (
              COALESCE(pg_get_expr(p.polqual, p.polrelid), '') LIKE '%public.is_group_%'
              OR COALESCE(pg_get_expr(p.polqual, p.polrelid), '') LIKE '%public.can_read_group%'
              OR COALESCE(pg_get_expr(p.polwithcheck, p.polrelid), '') LIKE '%public.is_group_%'
              OR COALESCE(pg_get_expr(p.polwithcheck, p.polrelid), '') LIKE '%public.can_read_group%'
          )
    LOOP
        using_expression := policy_row.using_expression;
        check_expression := policy_row.check_expression;

        IF using_expression IS NOT NULL THEN
            using_expression := replace(using_expression, 'public.is_group_member', 'private.is_group_member');
            using_expression := replace(using_expression, 'public.is_group_owner', 'private.is_group_owner');
            using_expression := replace(using_expression, 'public.is_group_admin', 'private.is_group_admin');
            using_expression := replace(using_expression, 'public.is_group_moderator', 'private.is_group_moderator');
            using_expression := replace(using_expression, 'public.can_read_group', 'private.can_read_group');
            EXECUTE format(
                'ALTER POLICY %I ON %I.%I USING (%s)',
                policy_row.policy_name,
                policy_row.schema_name,
                policy_row.table_name,
                using_expression
            );
        END IF;

        IF check_expression IS NOT NULL THEN
            check_expression := replace(check_expression, 'public.is_group_member', 'private.is_group_member');
            check_expression := replace(check_expression, 'public.is_group_owner', 'private.is_group_owner');
            check_expression := replace(check_expression, 'public.is_group_admin', 'private.is_group_admin');
            check_expression := replace(check_expression, 'public.is_group_moderator', 'private.is_group_moderator');
            check_expression := replace(check_expression, 'public.can_read_group', 'private.can_read_group');
            EXECUTE format(
                'ALTER POLICY %I ON %I.%I WITH CHECK (%s)',
                policy_row.policy_name,
                policy_row.schema_name,
                policy_row.table_name,
                check_expression
            );
        END IF;
    END LOOP;
END;
$$;

-- Only the owning row fields may be supplied by authenticated clients on insert.
REVOKE INSERT ON public.group_members FROM anon, authenticated;
REVOKE INSERT ON public.group_papers FROM anon, authenticated;
REVOKE INSERT ON public.group_paper_votes FROM anon, authenticated;
REVOKE INSERT ON public.group_presentations FROM anon, authenticated;
REVOKE INSERT ON public.group_presentation_papers FROM anon, authenticated;
REVOKE INSERT ON public.group_session_rsvps FROM anon, authenticated;
REVOKE INSERT ON public.group_session_reviews FROM anon, authenticated;
REVOKE INSERT ON public.group_posts FROM anon, authenticated;
REVOKE INSERT ON public.group_post_comments FROM anon, authenticated;
REVOKE INSERT ON public.group_post_reactions FROM anon, authenticated;

GRANT INSERT (group_id, user_id, role) ON public.group_members TO authenticated;
GRANT INSERT (group_id, bibcode, added_by) ON public.group_papers TO authenticated;
GRANT INSERT (group_paper_id, user_id) ON public.group_paper_votes TO authenticated;
GRANT INSERT (group_id, bibcode, presenter_id, scheduled_at, title, ends_at, time_zone, agenda)
    ON public.group_presentations TO authenticated;
GRANT INSERT (group_id, presentation_id, group_paper_id)
    ON public.group_presentation_papers TO authenticated;
GRANT INSERT (presentation_id, user_id, response) ON public.group_session_rsvps TO authenticated;
GRANT INSERT (group_id, bibcode, reviewer_id, notes, rating)
    ON public.group_session_reviews TO authenticated;
GRANT INSERT (group_id, author_id, post_type, body) ON public.group_posts TO authenticated;
GRANT INSERT (post_id, author_id, body) ON public.group_post_comments TO authenticated;
GRANT INSERT (post_id, user_id, reaction) ON public.group_post_reactions TO authenticated;

ALTER POLICY group_posts_insert_member ON public.group_posts
    WITH CHECK (
        author_id = auth.uid()
        AND private.is_group_member(group_id)
        AND NOT is_pinned
        AND (post_type = 'discussion' OR private.is_group_moderator(group_id))
    );

-- The private helper schema must not be added to the project's exposed Data API schemas.
-- Authenticated users need EXECUTE for RLS, but cannot call these helpers through PostgREST.
REVOKE ALL ON FUNCTION public.is_group_member(UUID) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.is_group_owner(UUID) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.is_group_admin(UUID) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.is_group_moderator(UUID) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.can_read_group(UUID) FROM PUBLIC, anon, authenticated;

COMMIT;