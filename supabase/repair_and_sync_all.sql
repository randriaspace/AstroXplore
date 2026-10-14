-- ============================================================================
-- AstroXplore Single Idempotent Database Repair & Synchronization Script
-- ============================================================================
-- Purpose:
--   Runs seamlessly in Supabase SQL Editor to create or repair all tables,
--   columns, indexes, Row-Level Security (RLS) policies, and RPC stored
--   procedures without dropping existing tables or causing data loss.
--
-- Safety:
--   - Uses CREATE TABLE IF NOT EXISTS
--   - Uses ALTER TABLE ... ADD COLUMN IF NOT EXISTS
--   - Uses CREATE OR REPLACE FUNCTION
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
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    reviewed_at TIMESTAMPTZ,
    reviewed_by UUID REFERENCES auth.users(id) ON DELETE SET NULL,
    CONSTRAINT group_join_requests_group_user_key UNIQUE (group_id, user_id)
);

-- ----------------------------------------------------------------------------
-- 7. Group Papers (Nominated Preprints for Discussion Shelf)
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS public.group_papers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id UUID NOT NULL REFERENCES public.groups(id) ON DELETE CASCADE,
    bibcode TEXT NOT NULL,
    added_by UUID REFERENCES auth.users(id) ON DELETE SET NULL,
    vote_count INTEGER NOT NULL DEFAULT 0,
    title TEXT,
    authors TEXT,
    year TEXT,
    added_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT group_papers_group_bibcode_key UNIQUE (group_id, bibcode)
);

ALTER TABLE public.group_papers ADD COLUMN IF NOT EXISTS title TEXT;
ALTER TABLE public.group_papers ADD COLUMN IF NOT EXISTS authors TEXT;
ALTER TABLE public.group_papers ADD COLUMN IF NOT EXISTS year TEXT;
ALTER TABLE public.group_papers ADD COLUMN IF NOT EXISTS vote_count INTEGER NOT NULL DEFAULT 0;

-- ----------------------------------------------------------------------------
-- 8. Group Paper Consensus Votes
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS public.group_paper_votes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id UUID REFERENCES public.groups(id) ON DELETE CASCADE,
    bibcode TEXT,
    group_paper_id UUID REFERENCES public.group_papers(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE public.group_paper_votes ADD COLUMN IF NOT EXISTS group_id UUID REFERENCES public.groups(id) ON DELETE CASCADE;
ALTER TABLE public.group_paper_votes ADD COLUMN IF NOT EXISTS bibcode TEXT;
ALTER TABLE public.group_paper_votes ADD COLUMN IF NOT EXISTS group_paper_id UUID REFERENCES public.group_papers(id) ON DELETE CASCADE;

-- ----------------------------------------------------------------------------
-- 9. Seminar Presentations & Meetings
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS public.group_presentations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id UUID NOT NULL REFERENCES public.groups(id) ON DELETE CASCADE,
    bibcode TEXT NOT NULL,
    presenter_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    title TEXT,
    scheduled_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ,
    time_zone TEXT NOT NULL DEFAULT 'UTC',
    agenda TEXT,
    meeting_location TEXT DEFAULT 'Virtual Seminar Room',
    status TEXT NOT NULL DEFAULT 'scheduled',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE public.group_presentations ADD COLUMN IF NOT EXISTS meeting_location TEXT DEFAULT 'Virtual Seminar Room';

-- ----------------------------------------------------------------------------
-- 10. Session RSVPs & Attendance Check-In
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS public.group_session_rsvps (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    presentation_id UUID NOT NULL REFERENCES public.group_presentations(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    response TEXT NOT NULL DEFAULT 'attending',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT group_session_rsvps_session_user_key UNIQUE (presentation_id, user_id)
);

CREATE TABLE IF NOT EXISTS public.group_session_attendance (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    presentation_id UUID NOT NULL REFERENCES public.group_presentations(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    checked_in_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT group_session_attendance_session_user_key UNIQUE (presentation_id, user_id)
);

-- ----------------------------------------------------------------------------
-- 11. Peer Reviews & Session Discussion Recaps
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
-- 12. Drop Existing Functions to Avoid Return Type Mismatch (Error 42P13)
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
-- 13. Security & Role Helper Functions
-- ----------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION private.is_group_admin(p_group_id UUID)
RETURNS BOOLEAN
LANGUAGE SQL
STABLE
SECURITY DEFINER
SET search_path = pg_catalog, public, auth
AS $$
    SELECT auth.uid() IS NOT NULL AND (
        EXISTS (
            SELECT 1 FROM public.groups AS g
            WHERE g.id = p_group_id AND g.owner_id = auth.uid()
        )
        OR EXISTS (
            SELECT 1 FROM public.group_members AS gm
            WHERE gm.group_id = p_group_id
              AND gm.user_id = auth.uid()
              AND gm.role IN ('owner', 'admin')
        )
    );
$$;

CREATE OR REPLACE FUNCTION public.is_group_member(p_group_id UUID)
RETURNS BOOLEAN
LANGUAGE SQL
STABLE
SECURITY DEFINER
SET search_path = pg_catalog, public, auth
AS $$
    SELECT auth.uid() IS NOT NULL AND (
        EXISTS (
            SELECT 1 FROM public.groups AS g
            WHERE g.id = p_group_id AND g.owner_id = auth.uid()
        )
        OR EXISTS (
            SELECT 1 FROM public.group_members AS gm
            WHERE gm.group_id = p_group_id AND gm.user_id = auth.uid()
        )
    );
$$;

CREATE OR REPLACE FUNCTION public.can_read_group(p_group_id UUID)
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
              COALESCE(g.visibility, 'public') = 'public'
              OR COALESCE(g.is_public, TRUE) = TRUE
              OR g.owner_id = auth.uid()
              OR public.is_group_member(g.id)
          )
    );
$$;

-- ----------------------------------------------------------------------------
-- 14. Stored Procedures (RPCs)
-- ----------------------------------------------------------------------------

-- RPC: create_group
CREATE OR REPLACE FUNCTION public.create_group(
    p_name TEXT,
    p_description TEXT DEFAULT NULL,
    p_focus_area TEXT DEFAULT NULL,
    p_visibility TEXT DEFAULT 'public'
)
RETURNS SETOF public.groups
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public, auth
AS $$
DECLARE
    v_user_id UUID := auth.uid();
    v_display_id TEXT;
    v_group public.groups%ROWTYPE;
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Authentication required' USING ERRCODE = '42501';
    END IF;

    -- Generate human-readable uppercase display ID
    v_display_id := 'ASTRO' || UPPER(SUBSTRING(REPLACE(gen_random_uuid()::TEXT, '-', ''), 1, 5));

    INSERT INTO public.groups (display_id, name, description, owner_id, focus_area, visibility, is_public, member_count)
    VALUES (v_display_id, TRIM(p_name), p_description, v_user_id, p_focus_area, p_visibility, (p_visibility = 'public'), 1)
    RETURNING * INTO v_group;

    -- Automatically enroll creator as Admin
    INSERT INTO public.group_members (group_id, user_id, role)
    VALUES (v_group.id, v_user_id, 'admin')
    ON CONFLICT (group_id, user_id) DO UPDATE SET role = 'admin';

    RETURN NEXT v_group;
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
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Authentication required' USING ERRCODE = '42501';
    END IF;

    -- If already member, return immediately
    IF EXISTS (SELECT 1 FROM public.group_members WHERE group_id = p_group_id AND user_id = v_user_id) THEN
        RETURN TRUE;
    END IF;

    INSERT INTO public.group_join_requests (group_id, user_id, status)
    VALUES (p_group_id, v_user_id, 'pending')
    ON CONFLICT (group_id, user_id) DO UPDATE SET status = 'pending';

    RETURN TRUE;
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
    v_group_id UUID;
    v_applicant_id UUID;
BEGIN
    SELECT group_id, user_id INTO v_group_id, v_applicant_id
    FROM public.group_join_requests
    WHERE id = p_request_id;

    IF v_group_id IS NULL THEN
        RAISE EXCEPTION 'Join request not found.' USING ERRCODE = 'P0002';
    END IF;

    -- Permission check: caller must be admin or owner
    IF NOT private.is_group_admin(v_group_id) THEN
        RAISE EXCEPTION 'Unauthorized: Only club admins can approve join requests.' USING ERRCODE = '42501';
    END IF;

    UPDATE public.group_join_requests
    SET status = 'accepted', reviewed_at = NOW(), reviewed_by = auth.uid()
    WHERE id = p_request_id;

    INSERT INTO public.group_members (group_id, user_id, role)
    VALUES (v_group_id, v_applicant_id, 'member')
    ON CONFLICT (group_id, user_id) DO UPDATE SET role = 'member';

    UPDATE public.groups
    SET member_count = (SELECT COUNT(*) FROM public.group_members WHERE group_id = v_group_id)
    WHERE id = v_group_id;

    RETURN TRUE;
END;
$$;

-- RPC: reject_group_join_request
CREATE OR REPLACE FUNCTION public.reject_group_join_request(p_request_id UUID)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public, auth
AS $$
DECLARE
    v_group_id UUID;
BEGIN
    SELECT group_id INTO v_group_id
    FROM public.group_join_requests
    WHERE id = p_request_id;

    IF v_group_id IS NULL THEN
        RAISE EXCEPTION 'Join request not found.' USING ERRCODE = 'P0002';
    END IF;

    IF NOT private.is_group_admin(v_group_id) THEN
        RAISE EXCEPTION 'Unauthorized: Only club admins can reject join requests.' USING ERRCODE = '42501';
    END IF;

    UPDATE public.group_join_requests
    SET status = 'rejected', reviewed_at = NOW(), reviewed_by = auth.uid()
    WHERE id = p_request_id;

    RETURN TRUE;
END;
$$;

-- RPC: toggle_paper_vote
CREATE OR REPLACE FUNCTION public.toggle_paper_vote(p_group_id UUID, p_bibcode TEXT)
RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public, auth
AS $$
DECLARE
    v_user_id UUID := auth.uid();
BEGIN
    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Authentication required.' USING ERRCODE = '42501';
    END IF;

    IF EXISTS (
        SELECT 1 FROM public.group_paper_votes
        WHERE group_id = p_group_id AND bibcode = p_bibcode AND user_id = v_user_id
    ) THEN
        DELETE FROM public.group_paper_votes
        WHERE group_id = p_group_id AND bibcode = p_bibcode AND user_id = v_user_id;

        UPDATE public.group_papers
        SET vote_count = (SELECT COUNT(*) FROM public.group_paper_votes WHERE group_id = p_group_id AND bibcode = p_bibcode)
        WHERE group_id = p_group_id AND bibcode = p_bibcode;

        RETURN FALSE;
    ELSE
        INSERT INTO public.group_paper_votes (group_id, bibcode, user_id)
        VALUES (p_group_id, p_bibcode, v_user_id);

        UPDATE public.group_papers
        SET vote_count = (SELECT COUNT(*) FROM public.group_paper_votes WHERE group_id = p_group_id AND bibcode = p_bibcode)
        WHERE group_id = p_group_id AND bibcode = p_bibcode;

        RETURN TRUE;
    END IF;
END;
$$;

-- RPC: check_in_session
CREATE OR REPLACE FUNCTION public.check_in_session(p_presentation_id UUID)
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

    INSERT INTO public.group_session_attendance (presentation_id, user_id)
    VALUES (p_presentation_id, v_user_id)
    ON CONFLICT (presentation_id, user_id) DO UPDATE SET checked_in_at = NOW();

    RETURN TRUE;
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
        RAISE EXCEPTION 'Unauthorized: Only club admins can change roles' USING ERRCODE = '42501';
    END IF;

    IF p_new_role NOT IN ('admin', 'moderator', 'member') THEN
        RAISE EXCEPTION 'Invalid role' USING ERRCODE = '22023';
    END IF;

    UPDATE public.group_members
    SET role = p_new_role
    WHERE group_id = p_group_id AND user_id = p_user_id;

    RETURN TRUE;
END;
$$;

-- RPC: remove_group_member (kick member)
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
    IF NOT private.is_group_admin(p_group_id) THEN
        RAISE EXCEPTION 'Unauthorized: Only club admins can remove members' USING ERRCODE = '42501';
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
    -- Only group owner can archive/dissolve
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
-- 15. Row Level Security (RLS) & Permissions
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
