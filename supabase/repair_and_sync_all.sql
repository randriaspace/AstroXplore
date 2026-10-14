-- ============================================================================
-- AstroXplore Single Idempotent Database Repair & Synchronization Script
-- ============================================================================
-- Purpose:
--   Runs seamlessly in Supabase SQL Editor to create or repair all tables,
--   columns, indexes, triggers, Row-Level Security (RLS) policies, and RPC
--   stored procedures without dropping existing tables or causing data loss.
--
-- Safety:
--   - Uses CREATE TABLE IF NOT EXISTS
--   - Uses ALTER TABLE ... ADD COLUMN IF NOT EXISTS
--   - Drops obsolete function signatures before recreating (avoids Error 42P13)
--   - Automatically creates and backfills profiles for all auth.users
--   - Completely idempotent: safe to run repeatedly on empty or live databases
-- ============================================================================

BEGIN;

CREATE SCHEMA IF NOT EXISTS extensions;
CREATE SCHEMA IF NOT EXISTS private;
CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA extensions;

-- ----------------------------------------------------------------------------
-- 1. Profiles & User Accounts
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS public.profiles (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    email TEXT NOT NULL UNIQUE,
    full_name TEXT,
    first_name TEXT,
    last_name TEXT,
    institution TEXT,
    affiliation_type TEXT,
    affiliation_name TEXT,
    country TEXT,
    education_level TEXT,
    orcid_id TEXT,
    is_onboarded BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS full_name TEXT;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS first_name TEXT;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS last_name TEXT;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS institution TEXT;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS affiliation_type TEXT;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS affiliation_name TEXT;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS country TEXT;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS education_level TEXT;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS orcid_id TEXT;
ALTER TABLE public.profiles ADD COLUMN IF NOT EXISTS is_onboarded BOOLEAN NOT NULL DEFAULT FALSE;

-- ----------------------------------------------------------------------------
-- 2. Research Keywords & User Preferences
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS public.keywords (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL UNIQUE,
    category TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS public.user_preferences (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    keyword_tag TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT user_preferences_user_keyword_key UNIQUE (user_id, keyword_tag)
);

-- ----------------------------------------------------------------------------
-- 3. Personal Library & Saved Preprints
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS public.saved_papers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    bibcode TEXT NOT NULL,
    title TEXT,
    authors TEXT,
    abstract TEXT,
    category TEXT,
    date_display TEXT,
    citation_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT saved_papers_user_bibcode_key UNIQUE (user_id, bibcode)
);

ALTER TABLE public.saved_papers ADD COLUMN IF NOT EXISTS title TEXT;
ALTER TABLE public.saved_papers ADD COLUMN IF NOT EXISTS authors TEXT;
ALTER TABLE public.saved_papers ADD COLUMN IF NOT EXISTS abstract TEXT;
ALTER TABLE public.saved_papers ADD COLUMN IF NOT EXISTS category TEXT;
ALTER TABLE public.saved_papers ADD COLUMN IF NOT EXISTS date_display TEXT;
ALTER TABLE public.saved_papers ADD COLUMN IF NOT EXISTS citation_count INTEGER NOT NULL DEFAULT 0;

-- ----------------------------------------------------------------------------
-- 4. Journal Clubs (Groups)
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS public.groups (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    display_id TEXT UNIQUE NOT NULL,
    name TEXT NOT NULL,
    description TEXT,
    owner_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    focus_area TEXT,
    visibility TEXT NOT NULL DEFAULT 'public',
    status TEXT NOT NULL DEFAULT 'active',
    is_public BOOLEAN NOT NULL DEFAULT TRUE,
    member_count INTEGER NOT NULL DEFAULT 1,
    meeting_schedule TEXT,
    meeting_location TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE public.groups ADD COLUMN IF NOT EXISTS visibility TEXT NOT NULL DEFAULT 'public';
ALTER TABLE public.groups ADD COLUMN IF NOT EXISTS is_public BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE public.groups ADD COLUMN IF NOT EXISTS status TEXT NOT NULL DEFAULT 'active';
ALTER TABLE public.groups ADD COLUMN IF NOT EXISTS focus_area TEXT;
ALTER TABLE public.groups ADD COLUMN IF NOT EXISTS meeting_schedule TEXT;
ALTER TABLE public.groups ADD COLUMN IF NOT EXISTS meeting_location TEXT;
ALTER TABLE public.groups ADD COLUMN IF NOT EXISTS member_count INTEGER NOT NULL DEFAULT 1;

-- ----------------------------------------------------------------------------
-- 5. Group Members & Roles (Owner, Admin, Moderator, Member)
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS public.group_members (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id UUID NOT NULL REFERENCES public.groups(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    role TEXT NOT NULL DEFAULT 'member',
    joined_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT group_members_group_user_key UNIQUE (group_id, user_id)
);

ALTER TABLE public.group_members ADD COLUMN IF NOT EXISTS role TEXT NOT NULL DEFAULT 'member';

-- ----------------------------------------------------------------------------
-- 6. Group Join Requests (Applicant Pipeline)
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS public.group_join_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id UUID NOT NULL REFERENCES public.groups(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    status TEXT NOT NULL DEFAULT 'pending',
    message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT group_join_requests_group_user_key UNIQUE (group_id, user_id)
);

-- ----------------------------------------------------------------------------
-- 7. Group Papers (Queue / Reading List)
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS public.group_papers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id UUID NOT NULL REFERENCES public.groups(id) ON DELETE CASCADE,
    bibcode TEXT NOT NULL,
    title TEXT,
    authors TEXT,
    abstract TEXT,
    added_by UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    votes_count INTEGER NOT NULL DEFAULT 0,
    status TEXT NOT NULL DEFAULT 'proposed',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT group_papers_group_bibcode_key UNIQUE (group_id, bibcode)
);

ALTER TABLE public.group_papers ADD COLUMN IF NOT EXISTS title TEXT;
ALTER TABLE public.group_papers ADD COLUMN IF NOT EXISTS authors TEXT;
ALTER TABLE public.group_papers ADD COLUMN IF NOT EXISTS abstract TEXT;
ALTER TABLE public.group_papers ADD COLUMN IF NOT EXISTS votes_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE public.group_papers ADD COLUMN IF NOT EXISTS status TEXT NOT NULL DEFAULT 'proposed';

-- ----------------------------------------------------------------------------
-- 8. Group Paper Votes
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS public.group_paper_votes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_paper_id UUID NOT NULL REFERENCES public.group_papers(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT group_paper_votes_paper_user_key UNIQUE (group_paper_id, user_id)
);

-- ----------------------------------------------------------------------------
-- 9. Group Sessions (Presentations)
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS public.group_presentations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id UUID NOT NULL REFERENCES public.groups(id) ON DELETE CASCADE,
    bibcode TEXT,
    title TEXT,
    presenter_id UUID REFERENCES auth.users(id) ON DELETE SET NULL,
    presenter_name TEXT,
    scheduled_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ,
    time_zone TEXT NOT NULL DEFAULT 'UTC',
    location TEXT,
    agenda TEXT,
    status TEXT NOT NULL DEFAULT 'scheduled',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE public.group_presentations ADD COLUMN IF NOT EXISTS presenter_name TEXT;
ALTER TABLE public.group_presentations ADD COLUMN IF NOT EXISTS ends_at TIMESTAMPTZ;
ALTER TABLE public.group_presentations ADD COLUMN IF NOT EXISTS location TEXT;
ALTER TABLE public.group_presentations ADD COLUMN IF NOT EXISTS agenda TEXT;
ALTER TABLE public.group_presentations ADD COLUMN IF NOT EXISTS status TEXT NOT NULL DEFAULT 'scheduled';

-- ----------------------------------------------------------------------------
-- 10. Session RSVPs & Attendance Tracking
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS public.group_session_rsvps (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    presentation_id UUID NOT NULL REFERENCES public.group_presentations(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    response TEXT NOT NULL DEFAULT 'attending',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT group_session_rsvps_presentation_user_key UNIQUE (presentation_id, user_id)
);

CREATE TABLE IF NOT EXISTS public.group_session_attendance (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    presentation_id UUID NOT NULL REFERENCES public.group_presentations(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    checked_in_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT group_session_attendance_presentation_user_key UNIQUE (presentation_id, user_id)
);

-- ----------------------------------------------------------------------------
-- 11. Session Reviews & Takeaways
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS public.group_session_reviews (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id UUID NOT NULL REFERENCES public.groups(id) ON DELETE CASCADE,
    bibcode TEXT NOT NULL,
    reviewer_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    notes TEXT NOT NULL,
    rating SMALLINT NOT NULL DEFAULT 5,
    takeaways TEXT[] DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE public.group_session_reviews ADD COLUMN IF NOT EXISTS takeaways TEXT[] DEFAULT '{}';

-- ----------------------------------------------------------------------------
-- 12. Automated Profile Creation & Backfill Trigger
-- ----------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public
AS $$
BEGIN
    INSERT INTO public.profiles (id, email, full_name, first_name, last_name, is_onboarded)
    VALUES (
        NEW.id,
        COALESCE(NEW.email, ''),
        COALESCE(NEW.raw_user_meta_data->>'full_name', ''),
        COALESCE(NEW.raw_user_meta_data->>'first_name', ''),
        COALESCE(NEW.raw_user_meta_data->>'last_name', ''),
        FALSE
    )
    ON CONFLICT (id) DO UPDATE SET
        email = EXCLUDED.email,
        full_name = COALESCE(NULLIF(EXCLUDED.full_name, ''), public.profiles.full_name),
        first_name = COALESCE(NULLIF(EXCLUDED.first_name, ''), public.profiles.first_name),
        last_name = COALESCE(NULLIF(EXCLUDED.last_name, ''), public.profiles.last_name);

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
    AFTER INSERT ON auth.users
    FOR EACH ROW EXECUTE FUNCTION public.handle_new_user();

GRANT EXECUTE ON FUNCTION public.handle_new_user() TO authenticated, service_role, anon;

-- Backfill any existing auth.users who do not have a public.profiles record
INSERT INTO public.profiles (id, email, full_name, first_name, last_name, is_onboarded)
SELECT 
    id, 
    COALESCE(email, ''), 
    COALESCE(raw_user_meta_data->>'full_name', ''), 
    COALESCE(raw_user_meta_data->>'first_name', ''), 
    COALESCE(raw_user_meta_data->>'last_name', ''), 
    FALSE
FROM auth.users
ON CONFLICT (id) DO NOTHING;

-- ----------------------------------------------------------------------------
-- 13. Drop Functions Before Creation to Prevent Return Type Error (42P13)
-- ----------------------------------------------------------------------------

DROP FUNCTION IF EXISTS public.check_in_session(UUID) CASCADE;
DROP FUNCTION IF EXISTS public.check_in_session CASCADE;
DROP FUNCTION IF EXISTS public.create_group(TEXT, TEXT, TEXT, TEXT) CASCADE;
DROP FUNCTION IF EXISTS public.create_group CASCADE;
DROP FUNCTION IF EXISTS public.submit_group_join_request(UUID) CASCADE;
DROP FUNCTION IF EXISTS public.submit_group_join_request CASCADE;
DROP FUNCTION IF EXISTS public.approve_group_join_request(UUID) CASCADE;
DROP FUNCTION IF EXISTS public.approve_group_join_request CASCADE;
DROP FUNCTION IF EXISTS public.reject_group_join_request(UUID) CASCADE;
DROP FUNCTION IF EXISTS public.reject_group_join_request CASCADE;
DROP FUNCTION IF EXISTS public.decline_group_join_request(UUID) CASCADE;
DROP FUNCTION IF EXISTS public.decline_group_join_request CASCADE;
DROP FUNCTION IF EXISTS public.toggle_paper_vote(UUID, TEXT) CASCADE;
DROP FUNCTION IF EXISTS public.toggle_paper_vote CASCADE;
DROP FUNCTION IF EXISTS public.set_group_member_role(UUID, UUID, TEXT) CASCADE;
DROP FUNCTION IF EXISTS public.set_group_member_role CASCADE;
DROP FUNCTION IF EXISTS public.remove_group_member(UUID, UUID) CASCADE;
DROP FUNCTION IF EXISTS public.remove_group_member CASCADE;
DROP FUNCTION IF EXISTS public.leave_group(UUID) CASCADE;
DROP FUNCTION IF EXISTS public.leave_group CASCADE;
DROP FUNCTION IF EXISTS public.archive_group(UUID) CASCADE;
DROP FUNCTION IF EXISTS public.archive_group CASCADE;
DROP FUNCTION IF EXISTS public.is_group_member(UUID) CASCADE;
DROP FUNCTION IF EXISTS public.is_group_member CASCADE;
DROP FUNCTION IF EXISTS public.can_read_group(UUID) CASCADE;
DROP FUNCTION IF EXISTS public.can_read_group CASCADE;
DROP FUNCTION IF EXISTS private.is_group_admin(UUID) CASCADE;
DROP FUNCTION IF EXISTS private.is_group_admin CASCADE;

-- ----------------------------------------------------------------------------
-- 14. Security & Role Helper Functions
-- ----------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION private.is_group_admin(p_group_id UUID)
RETURNS BOOLEAN
LANGUAGE SQL
STABLE
SECURITY DEFINER
SET search_path = pg_catalog, public, auth
AS $$
    SELECT EXISTS (
        SELECT 1 FROM public.groups
        WHERE id = p_group_id AND owner_id = auth.uid()
        UNION
        SELECT 1 FROM public.group_members
        WHERE group_id = p_group_id AND user_id = auth.uid() AND role IN ('owner', 'admin')
    );
$$;

CREATE OR REPLACE FUNCTION public.is_group_member(p_group_id UUID)
RETURNS BOOLEAN
LANGUAGE SQL
STABLE
SECURITY DEFINER
SET search_path = pg_catalog, public, auth
AS $$
    SELECT EXISTS (
        SELECT 1 FROM public.groups
        WHERE id = p_group_id AND owner_id = auth.uid()
        UNION
        SELECT 1 FROM public.group_members
        WHERE group_id = p_group_id AND user_id = auth.uid()
    );
$$;

CREATE OR REPLACE FUNCTION public.can_read_group(p_group_id UUID)
RETURNS BOOLEAN
LANGUAGE SQL
STABLE
SECURITY DEFINER
SET search_path = pg_catalog, public, auth
AS $$
    SELECT EXISTS (
        SELECT 1 FROM public.groups
        WHERE id = p_group_id AND (visibility = 'public' OR is_public = TRUE)
        UNION
        SELECT 1 FROM public.group_members
        WHERE group_id = p_group_id AND user_id = auth.uid()
        UNION
        SELECT 1 FROM public.groups
        WHERE id = p_group_id AND owner_id = auth.uid()
    );
$$;

-- ----------------------------------------------------------------------------
-- 15. Stored Procedures (RPCs)
-- ----------------------------------------------------------------------------

-- RPC: create_group
CREATE OR REPLACE FUNCTION public.create_group(
    p_name TEXT,
    p_description TEXT DEFAULT NULL,
    p_focus_area TEXT DEFAULT NULL,
    p_visibility TEXT DEFAULT 'public'
)
RETURNS TABLE (
    id UUID,
    display_id TEXT,
    name TEXT,
    description TEXT,
    focus_area TEXT,
    visibility TEXT,
    status TEXT,
    is_public BOOLEAN,
    member_count INTEGER,
    owner_id UUID,
    created_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public, auth
AS $$
DECLARE
    v_user_id UUID := auth.uid();
    v_group_id UUID := gen_random_uuid();
    v_display_id TEXT := 'grp_' || substr(md5(random()::text), 1, 8);
    v_is_public BOOLEAN := (COALESCE(p_visibility, 'public') = 'public');
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Authentication required' USING ERRCODE = '42501';
    END IF;

    IF trim(COALESCE(p_name, '')) = '' THEN
        RAISE EXCEPTION 'Group name cannot be empty' USING ERRCODE = '22023';
    END IF;

    INSERT INTO public.groups (
        id, display_id, name, description, focus_area, visibility, status, is_public, member_count, owner_id
    ) VALUES (
        v_group_id, v_display_id, trim(p_name), p_description, p_focus_area, COALESCE(p_visibility, 'public'), 'active', v_is_public, 1, v_user_id
    );

    INSERT INTO public.group_members (group_id, user_id, role)
    VALUES (v_group_id, v_user_id, 'owner')
    ON CONFLICT (group_id, user_id) DO UPDATE SET role = 'owner';

    RETURN QUERY
    SELECT g.id, g.display_id, g.name, g.description, g.focus_area, g.visibility, g.status, g.is_public, g.member_count, g.owner_id, g.created_at, g.updated_at
    FROM public.groups g
    WHERE g.id = v_group_id;
END;
$$;

-- RPC: submit_group_join_request
CREATE OR REPLACE FUNCTION public.submit_group_join_request(p_group_id UUID)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public, auth
AS $$
DECLARE
    v_user_id UUID := auth.uid();
    v_visibility TEXT;
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Authentication required' USING ERRCODE = '42501';
    END IF;

    SELECT visibility INTO v_visibility FROM public.groups WHERE id = p_group_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Group not found' USING ERRCODE = 'P0002';
    END IF;

    IF EXISTS (SELECT 1 FROM public.group_members WHERE group_id = p_group_id AND user_id = v_user_id) THEN
        RETURN TRUE;
    END IF;

    IF v_visibility = 'public' THEN
        INSERT INTO public.group_members (group_id, user_id, role)
        VALUES (p_group_id, v_user_id, 'member')
        ON CONFLICT (group_id, user_id) DO NOTHING;

        UPDATE public.groups
        SET member_count = (SELECT COUNT(*) FROM public.group_members WHERE group_id = p_group_id)
        WHERE id = p_group_id;

        RETURN TRUE;
    ELSE
        INSERT INTO public.group_join_requests (group_id, user_id, status)
        VALUES (p_group_id, v_user_id, 'pending')
        ON CONFLICT (group_id, user_id) DO UPDATE SET status = 'pending', updated_at = NOW();

        RETURN TRUE;
    END IF;
END;
$$;

-- RPC: approve_group_join_request
CREATE OR REPLACE FUNCTION public.approve_group_join_request(p_request_id UUID)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public, auth
AS $$
DECLARE
    v_req RECORD;
BEGIN
    SELECT * INTO v_req FROM public.group_join_requests WHERE id = p_request_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Join request not found' USING ERRCODE = 'P0002';
    END IF;

    IF NOT private.is_group_admin(v_req.group_id) THEN
        RAISE EXCEPTION 'Unauthorized: Group admin privileges required' USING ERRCODE = '42501';
    END IF;

    INSERT INTO public.group_members (group_id, user_id, role)
    VALUES (v_req.group_id, v_req.user_id, 'member')
    ON CONFLICT (group_id, user_id) DO NOTHING;

    UPDATE public.group_join_requests
    SET status = 'approved', updated_at = NOW()
    WHERE id = p_request_id;

    UPDATE public.groups
    SET member_count = (SELECT COUNT(*) FROM public.group_members WHERE group_id = v_req.group_id)
    WHERE id = v_req.group_id;

    RETURN TRUE;
END;
$$;

-- RPC: decline_group_join_request
CREATE OR REPLACE FUNCTION public.decline_group_join_request(p_request_id UUID)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public, auth
AS $$
DECLARE
    v_req RECORD;
BEGIN
    SELECT * INTO v_req FROM public.group_join_requests WHERE id = p_request_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Join request not found' USING ERRCODE = 'P0002';
    END IF;

    IF NOT private.is_group_admin(v_req.group_id) THEN
        RAISE EXCEPTION 'Unauthorized: Group admin privileges required' USING ERRCODE = '42501';
    END IF;

    UPDATE public.group_join_requests
    SET status = 'declined', updated_at = NOW()
    WHERE id = p_request_id;

    RETURN TRUE;
END;
$$;

-- RPC: toggle_paper_vote
CREATE OR REPLACE FUNCTION public.toggle_paper_vote(
    p_group_id UUID,
    p_bibcode TEXT
)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public, auth
AS $$
DECLARE
    v_user_id UUID := auth.uid();
    v_group_paper_id UUID;
    v_voted BOOLEAN;
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Authentication required' USING ERRCODE = '42501';
    END IF;

    SELECT id INTO v_group_paper_id
    FROM public.group_papers
    WHERE group_id = p_group_id AND bibcode = p_bibcode;

    IF NOT FOUND THEN
        INSERT INTO public.group_papers (group_id, bibcode, added_by, votes_count)
        VALUES (p_group_id, p_bibcode, v_user_id, 0)
        RETURNING id INTO v_group_paper_id;
    END IF;

    SELECT EXISTS (
        SELECT 1 FROM public.group_paper_votes
        WHERE group_paper_id = v_group_paper_id AND user_id = v_user_id
    ) INTO v_voted;

    IF v_voted THEN
        DELETE FROM public.group_paper_votes
        WHERE group_paper_id = v_group_paper_id AND user_id = v_user_id;

        UPDATE public.group_papers
        SET votes_count = GREATEST(0, (SELECT COUNT(*) FROM public.group_paper_votes WHERE group_paper_id = v_group_paper_id))
        WHERE id = v_group_paper_id;

        RETURN FALSE;
    ELSE
        INSERT INTO public.group_paper_votes (group_paper_id, user_id)
        VALUES (v_group_paper_id, v_user_id);

        UPDATE public.group_papers
        SET votes_count = (SELECT COUNT(*) FROM public.group_paper_votes WHERE group_paper_id = v_group_paper_id)
        WHERE id = v_group_paper_id;

        RETURN TRUE;
    END IF;
END;
$$;

-- RPC: check_in_session (Defined as RETURNS VOID to be 100% compatible with existing schemas)
CREATE OR REPLACE FUNCTION public.check_in_session(p_presentation_id UUID)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public, auth
AS $$
DECLARE
    v_user_id UUID := auth.uid();
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Authentication required' USING ERRCODE = '42501';
    END IF;

    INSERT INTO public.group_session_attendance (presentation_id, user_id)
    VALUES (p_presentation_id, v_user_id)
    ON CONFLICT (presentation_id, user_id) DO UPDATE SET checked_in_at = NOW();
END;
$$;

-- RPC: set_group_member_role
CREATE OR REPLACE FUNCTION public.set_group_member_role(
    p_group_id UUID,
    p_user_id UUID,
    p_new_role TEXT
)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public, auth
AS $$
BEGIN
    IF NOT private.is_group_admin(p_group_id) THEN
        RAISE EXCEPTION 'Unauthorized: Only club admins or owners can change roles' USING ERRCODE = '42501';
    END IF;

    IF p_new_role NOT IN ('owner', 'admin', 'moderator', 'member') THEN
        RAISE EXCEPTION 'Invalid role specified: %', p_new_role USING ERRCODE = '22023';
    END IF;

    UPDATE public.group_members
    SET role = p_new_role
    WHERE group_id = p_group_id AND user_id = p_user_id;

    RETURN TRUE;
END;
$$;

-- RPC: remove_group_member
CREATE OR REPLACE FUNCTION public.remove_group_member(
    p_group_id UUID,
    p_user_id UUID
)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public, auth
AS $$
BEGIN
    IF NOT private.is_group_admin(p_group_id) AND auth.uid() != p_user_id THEN
        RAISE EXCEPTION 'Unauthorized: Only club admins can remove other members' USING ERRCODE = '42501';
    END IF;

    DELETE FROM public.group_members
    WHERE group_id = p_group_id AND user_id = p_user_id;

    UPDATE public.groups
    SET member_count = (SELECT COUNT(*) FROM public.group_members WHERE group_id = p_group_id)
    WHERE id = p_group_id;

    RETURN TRUE;
END;
$$;

-- RPC: leave_group
CREATE OR REPLACE FUNCTION public.leave_group(p_group_id UUID)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public, auth
AS $$
DECLARE
    v_user_id UUID := auth.uid();
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Authentication required' USING ERRCODE = '42501';
    END IF;

    DELETE FROM public.group_members
    WHERE group_id = p_group_id AND user_id = v_user_id;

    UPDATE public.groups
    SET member_count = (SELECT COUNT(*) FROM public.group_members WHERE group_id = p_group_id)
    WHERE id = p_group_id;

    RETURN TRUE;
END;
$$;

-- RPC: archive_group
CREATE OR REPLACE FUNCTION public.archive_group(p_group_id UUID)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public, auth
AS $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM public.groups WHERE id = p_group_id AND owner_id = auth.uid()) THEN
        RAISE EXCEPTION 'Unauthorized: Only the club founder can archive this club' USING ERRCODE = '42501';
    END IF;

    UPDATE public.groups
    SET status = 'archived'
    WHERE id = p_group_id;

    RETURN TRUE;
END;
$$;

-- ----------------------------------------------------------------------------
-- 16. Row Level Security (RLS) & Policies
-- ----------------------------------------------------------------------------

ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.keywords ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_preferences ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.saved_papers ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.groups ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.group_members ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.group_join_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.group_papers ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.group_paper_votes ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.group_presentations ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.group_session_rsvps ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.group_session_attendance ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.group_session_reviews ENABLE ROW LEVEL SECURITY;

-- Profiles: Authenticated users can read profiles; owners can insert/update/delete their own
DROP POLICY IF EXISTS profiles_select_authenticated ON public.profiles;
DROP POLICY IF EXISTS profiles_select_own ON public.profiles;
DROP POLICY IF EXISTS profiles_select_policy ON public.profiles;
CREATE POLICY profiles_select_authenticated ON public.profiles
    FOR SELECT TO authenticated
    USING (auth.uid() IS NOT NULL);

DROP POLICY IF EXISTS profiles_insert_own ON public.profiles;
DROP POLICY IF EXISTS profiles_insert_policy ON public.profiles;
CREATE POLICY profiles_insert_own ON public.profiles
    FOR INSERT TO authenticated
    WITH CHECK (auth.uid() = id);

DROP POLICY IF EXISTS profiles_update_own ON public.profiles;
DROP POLICY IF EXISTS profiles_update_policy ON public.profiles;
CREATE POLICY profiles_update_own ON public.profiles
    FOR UPDATE TO authenticated
    USING (auth.uid() = id)
    WITH CHECK (auth.uid() = id);

DROP POLICY IF EXISTS profiles_delete_own ON public.profiles;
DROP POLICY IF EXISTS profiles_delete_policy ON public.profiles;
CREATE POLICY profiles_delete_own ON public.profiles
    FOR DELETE TO authenticated
    USING (auth.uid() = id);

-- Keywords: Read by all, insert by authenticated
DROP POLICY IF EXISTS keywords_read ON public.keywords;
CREATE POLICY keywords_read ON public.keywords
    FOR SELECT TO anon, authenticated
    USING (TRUE);

-- User Preferences: Private to owner
DROP POLICY IF EXISTS user_preferences_all_own ON public.user_preferences;
CREATE POLICY user_preferences_all_own ON public.user_preferences
    FOR ALL TO authenticated
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

-- Saved Papers: Private to owner
DROP POLICY IF EXISTS saved_papers_all_own ON public.saved_papers;
CREATE POLICY saved_papers_all_own ON public.saved_papers
    FOR ALL TO authenticated
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

-- Groups: Select visible groups, manage own
DROP POLICY IF EXISTS groups_select_policy ON public.groups;
CREATE POLICY groups_select_policy ON public.groups
    FOR SELECT TO authenticated
    USING (visibility = 'public' OR is_public = TRUE OR owner_id = auth.uid() OR public.is_group_member(id));

DROP POLICY IF EXISTS groups_insert_policy ON public.groups;
CREATE POLICY groups_insert_policy ON public.groups
    FOR INSERT TO authenticated
    WITH CHECK (auth.uid() = owner_id);

DROP POLICY IF EXISTS groups_update_policy ON public.groups;
CREATE POLICY groups_update_policy ON public.groups
    FOR UPDATE TO authenticated
    USING (auth.uid() = owner_id OR private.is_group_admin(id))
    WITH CHECK (auth.uid() = owner_id OR private.is_group_admin(id));

-- Group Members
DROP POLICY IF EXISTS group_members_select_policy ON public.group_members;
CREATE POLICY group_members_select_policy ON public.group_members
    FOR SELECT TO authenticated
    USING (public.can_read_group(group_id));

DROP POLICY IF EXISTS group_members_insert_policy ON public.group_members;
CREATE POLICY group_members_insert_policy ON public.group_members
    FOR INSERT TO authenticated
    WITH CHECK (auth.uid() = user_id OR private.is_group_admin(group_id));

DROP POLICY IF EXISTS group_members_update_policy ON public.group_members;
CREATE POLICY group_members_update_policy ON public.group_members
    FOR UPDATE TO authenticated
    USING (private.is_group_admin(group_id));

DROP POLICY IF EXISTS group_members_delete_policy ON public.group_members;
CREATE POLICY group_members_delete_policy ON public.group_members
    FOR DELETE TO authenticated
    USING (auth.uid() = user_id OR private.is_group_admin(group_id));

-- Group Join Requests
DROP POLICY IF EXISTS join_requests_select_policy ON public.group_join_requests;
CREATE POLICY join_requests_select_policy ON public.group_join_requests
    FOR SELECT TO authenticated
    USING (auth.uid() = user_id OR private.is_group_admin(group_id));

DROP POLICY IF EXISTS join_requests_insert_policy ON public.group_join_requests;
CREATE POLICY join_requests_insert_policy ON public.group_join_requests
    FOR INSERT TO authenticated
    WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS join_requests_update_policy ON public.group_join_requests;
CREATE POLICY join_requests_update_policy ON public.group_join_requests
    FOR UPDATE TO authenticated
    USING (private.is_group_admin(group_id));

-- Group Papers, Votes, Sessions
DROP POLICY IF EXISTS group_papers_select_policy ON public.group_papers;
CREATE POLICY group_papers_select_policy ON public.group_papers
    FOR SELECT TO authenticated
    USING (public.can_read_group(group_id));

DROP POLICY IF EXISTS group_papers_insert_policy ON public.group_papers;
CREATE POLICY group_papers_insert_policy ON public.group_papers
    FOR INSERT TO authenticated
    WITH CHECK (public.is_group_member(group_id));

DROP POLICY IF EXISTS group_paper_votes_all ON public.group_paper_votes;
CREATE POLICY group_paper_votes_all ON public.group_paper_votes
    FOR ALL TO authenticated
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS presentations_select_policy ON public.group_presentations;
CREATE POLICY presentations_select_policy ON public.group_presentations
    FOR SELECT TO authenticated
    USING (public.can_read_group(group_id));

DROP POLICY IF EXISTS session_rsvps_all ON public.group_session_rsvps;
CREATE POLICY session_rsvps_all ON public.group_session_rsvps
    FOR ALL TO authenticated
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS session_attendance_all ON public.group_session_attendance;
CREATE POLICY session_attendance_all ON public.group_session_attendance
    FOR ALL TO authenticated
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS session_reviews_all ON public.group_session_reviews;
CREATE POLICY session_reviews_all ON public.group_session_reviews
    FOR ALL TO authenticated
    USING (auth.uid() = reviewer_id OR private.is_group_admin(group_id))
    WITH CHECK (auth.uid() = reviewer_id);

-- Grants
GRANT ALL ON TABLE public.profiles TO authenticated, service_role;
GRANT ALL ON TABLE public.keywords TO authenticated, service_role;
GRANT ALL ON TABLE public.user_preferences TO authenticated, service_role;
GRANT ALL ON TABLE public.saved_papers TO authenticated, service_role;
GRANT ALL ON TABLE public.groups TO authenticated, service_role;
GRANT ALL ON TABLE public.group_members TO authenticated, service_role;
GRANT ALL ON TABLE public.group_join_requests TO authenticated, service_role;
GRANT ALL ON TABLE public.group_papers TO authenticated, service_role;
GRANT ALL ON TABLE public.group_paper_votes TO authenticated, service_role;
GRANT ALL ON TABLE public.group_presentations TO authenticated, service_role;
GRANT ALL ON TABLE public.group_session_rsvps TO authenticated, service_role;
GRANT ALL ON TABLE public.group_session_attendance TO authenticated, service_role;
GRANT ALL ON TABLE public.group_session_reviews TO authenticated, service_role;

GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO authenticated, anon, service_role;

COMMIT;
