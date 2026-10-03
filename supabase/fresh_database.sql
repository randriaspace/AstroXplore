-- AstroXplore fresh database setup for Supabase SQL Editor.
-- Run only after taking a backup and resetting/removing the app's public tables.
-- This script never drops tables or data. Supabase-managed auth.users/auth.uid() are required.
-- The Android app must call the invite/role RPCs described in the handoff notes before
-- private invite acceptance and role changes are available from the UI.

BEGIN;

DO $$
BEGIN
    IF to_regclass('public.profiles') IS NOT NULL
       OR to_regclass('public.groups') IS NOT NULL
       OR to_regclass('public.group_members') IS NOT NULL
       OR to_regclass('public.saved_papers') IS NOT NULL THEN
        RAISE EXCEPTION 'AstroXplore tables already exist. Back up and reset the app schema before running fresh_database.sql.';
    END IF;
END;
$$;

CREATE SCHEMA IF NOT EXISTS extensions;
CREATE SCHEMA IF NOT EXISTS private;
CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA extensions;

-- -----------------------------------------------------------------------------
-- Core account, preference, and personal library tables
-- -----------------------------------------------------------------------------

CREATE TABLE public.profiles (
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

CREATE TABLE public.keywords (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL UNIQUE,
    category TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE public.user_preferences (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    keyword_tag TEXT NOT NULL REFERENCES public.keywords(name) ON UPDATE CASCADE ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT user_preferences_user_keyword_key UNIQUE (user_id, keyword_tag)
);

CREATE TABLE public.saved_papers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    bibcode TEXT NOT NULL,
    title TEXT,
    authors TEXT,
    abstract TEXT,
    category TEXT,
    date_display TEXT,
    citation_count INTEGER NOT NULL DEFAULT 0 CHECK (citation_count >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT saved_papers_user_bibcode_key UNIQUE (user_id, bibcode)
);

-- -----------------------------------------------------------------------------
-- Journal clubs and membership
-- -----------------------------------------------------------------------------

CREATE TABLE public.groups (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    display_id TEXT NOT NULL UNIQUE,
    name TEXT NOT NULL CHECK (length(btrim(name)) BETWEEN 2 AND 100),
    description TEXT CHECK (description IS NULL OR length(description) <= 2000),
    owner_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    focus_area TEXT CHECK (focus_area IS NULL OR length(focus_area) <= 120),
    visibility TEXT NOT NULL DEFAULT 'private' CHECK (visibility IN ('private', 'public')),
    status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'archived')),
    member_count INTEGER NOT NULL DEFAULT 0 CHECK (member_count >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT groups_display_id_format CHECK (display_id ~ '^[A-Z0-9]{6,16}$')
);

CREATE TABLE public.group_members (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id UUID NOT NULL REFERENCES public.groups(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    role TEXT NOT NULL DEFAULT 'member' CHECK (role IN ('admin', 'moderator', 'member')),
    joined_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT group_members_group_user_key UNIQUE (group_id, user_id)
);

CREATE TABLE public.group_invites (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id UUID NOT NULL REFERENCES public.groups(id) ON DELETE CASCADE,
    token_hash BYTEA NOT NULL UNIQUE,
    created_by UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    expires_at TIMESTAMPTZ NOT NULL,
    max_uses INTEGER NOT NULL DEFAULT 1 CHECK (max_uses BETWEEN 1 AND 500),
    uses_count INTEGER NOT NULL DEFAULT 0 CHECK (uses_count >= 0),
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT group_invites_uses_not_over_limit CHECK (uses_count <= max_uses)
);

-- -----------------------------------------------------------------------------
-- Shared papers and consensus voting
-- -----------------------------------------------------------------------------

CREATE TABLE public.group_papers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id UUID NOT NULL REFERENCES public.groups(id) ON DELETE CASCADE,
    bibcode TEXT NOT NULL,
    added_by UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    vote_count INTEGER NOT NULL DEFAULT 0 CHECK (vote_count >= 0),
    added_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT group_papers_group_bibcode_key UNIQUE (group_id, bibcode),
    CONSTRAINT group_papers_id_group_key UNIQUE (id, group_id)
);

CREATE TABLE public.group_paper_votes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_paper_id UUID NOT NULL REFERENCES public.group_papers(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT group_paper_votes_paper_user_key UNIQUE (group_paper_id, user_id)
);

-- -----------------------------------------------------------------------------
-- Club meetings, linked papers, attendance, and session reviews
-- -----------------------------------------------------------------------------

CREATE TABLE public.group_presentations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id UUID NOT NULL REFERENCES public.groups(id) ON DELETE CASCADE,
    bibcode TEXT NOT NULL,
    presenter_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    title TEXT CHECK (title IS NULL OR length(title) <= 200),
    scheduled_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ,
    time_zone TEXT NOT NULL DEFAULT 'UTC',
    agenda TEXT CHECK (agenda IS NULL OR length(agenda) <= 5000),
    status TEXT NOT NULL DEFAULT 'scheduled' CHECK (status IN ('scheduled', 'completed', 'cancelled')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT group_presentations_end_after_start CHECK (ends_at IS NULL OR ends_at > scheduled_at),
    CONSTRAINT group_presentations_id_group_key UNIQUE (id, group_id)
);

CREATE TABLE public.group_presentation_papers (
    group_id UUID NOT NULL,
    presentation_id UUID NOT NULL,
    group_paper_id UUID NOT NULL,
    added_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (presentation_id, group_paper_id),
    CONSTRAINT presentation_papers_same_group_session
        FOREIGN KEY (presentation_id, group_id)
        REFERENCES public.group_presentations(id, group_id) ON DELETE CASCADE,
    CONSTRAINT presentation_papers_same_group_paper
        FOREIGN KEY (group_paper_id, group_id)
        REFERENCES public.group_papers(id, group_id) ON DELETE CASCADE
);

CREATE TABLE public.group_session_rsvps (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    presentation_id UUID NOT NULL REFERENCES public.group_presentations(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    response TEXT NOT NULL DEFAULT 'attending' CHECK (response IN ('attending', 'maybe', 'declined')),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT group_session_rsvps_session_user_key UNIQUE (presentation_id, user_id)
);

CREATE TABLE public.group_session_attendance (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    presentation_id UUID NOT NULL REFERENCES public.group_presentations(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    checked_in_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT group_session_attendance_session_user_key UNIQUE (presentation_id, user_id)
);

CREATE TABLE public.group_session_reviews (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id UUID NOT NULL REFERENCES public.groups(id) ON DELETE CASCADE,
    bibcode TEXT NOT NULL,
    reviewer_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    notes TEXT NOT NULL CHECK (length(btrim(notes)) BETWEEN 1 AND 10000),
    rating SMALLINT NOT NULL DEFAULT 5 CHECK (rating BETWEEN 1 AND 5),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- -----------------------------------------------------------------------------
-- Club discussion and reactions
-- -----------------------------------------------------------------------------

CREATE TABLE public.group_posts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id UUID NOT NULL REFERENCES public.groups(id) ON DELETE CASCADE,
    author_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    post_type TEXT NOT NULL DEFAULT 'discussion' CHECK (post_type IN ('discussion', 'announcement')),
    body TEXT NOT NULL CHECK (length(btrim(body)) BETWEEN 1 AND 10000),
    is_pinned BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE public.group_post_comments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id UUID NOT NULL REFERENCES public.group_posts(id) ON DELETE CASCADE,
    author_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    body TEXT NOT NULL CHECK (length(btrim(body)) BETWEEN 1 AND 5000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE public.group_post_reactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id UUID NOT NULL REFERENCES public.group_posts(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES public.profiles(id) ON DELETE CASCADE,
    reaction TEXT NOT NULL CHECK (reaction IN ('like', 'insightful', 'question')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT group_post_reactions_post_user_key UNIQUE (post_id, user_id)
);

-- -----------------------------------------------------------------------------
-- Indexes for member checks, common lists, and foreign-key joins
-- -----------------------------------------------------------------------------

CREATE INDEX user_preferences_user_id_idx ON public.user_preferences(user_id);
CREATE INDEX saved_papers_user_created_idx ON public.saved_papers(user_id, created_at DESC);
CREATE INDEX groups_owner_id_idx ON public.groups(owner_id);
CREATE INDEX groups_visibility_status_idx ON public.groups(visibility, status, created_at DESC);
CREATE INDEX group_members_user_joined_idx ON public.group_members(user_id, joined_at DESC);
CREATE INDEX group_members_group_role_idx ON public.group_members(group_id, role);
CREATE INDEX group_invites_group_expiry_idx ON public.group_invites(group_id, expires_at DESC);
CREATE INDEX group_papers_group_added_idx ON public.group_papers(group_id, added_at DESC);
CREATE INDEX group_paper_votes_user_id_idx ON public.group_paper_votes(user_id);
CREATE INDEX group_presentations_group_scheduled_idx ON public.group_presentations(group_id, scheduled_at);
CREATE INDEX group_presentation_papers_group_idx ON public.group_presentation_papers(group_id);
CREATE INDEX group_session_rsvps_user_id_idx ON public.group_session_rsvps(user_id);
CREATE INDEX group_session_attendance_user_id_idx ON public.group_session_attendance(user_id);
CREATE INDEX group_session_reviews_group_created_idx ON public.group_session_reviews(group_id, created_at DESC);
CREATE INDEX group_posts_group_created_idx ON public.group_posts(group_id, created_at DESC);
CREATE INDEX group_post_comments_post_created_idx ON public.group_post_comments(post_id, created_at);
CREATE INDEX group_post_reactions_user_id_idx ON public.group_post_reactions(user_id);

-- -----------------------------------------------------------------------------
-- Timestamp and aggregate maintenance triggers
-- -----------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION public.set_updated_at()
RETURNS TRIGGER
LANGUAGE plpgsql
SET search_path = pg_catalog, public
AS $$
BEGIN
    NEW.updated_at := NOW();
    RETURN NEW;
END;
$$;

CREATE TRIGGER profiles_set_updated_at
BEFORE UPDATE ON public.profiles
FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();
CREATE TRIGGER groups_set_updated_at
BEFORE UPDATE ON public.groups
FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();
CREATE TRIGGER presentations_set_updated_at
BEFORE UPDATE ON public.group_presentations
FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();
CREATE TRIGGER rsvps_set_updated_at
BEFORE UPDATE ON public.group_session_rsvps
FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();
CREATE TRIGGER reviews_set_updated_at
BEFORE UPDATE ON public.group_session_reviews
FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();
CREATE TRIGGER posts_set_updated_at
BEFORE UPDATE ON public.group_posts
FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();
CREATE TRIGGER comments_set_updated_at
BEFORE UPDATE ON public.group_post_comments
FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

CREATE OR REPLACE FUNCTION public.maintain_group_member_count()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public
AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        UPDATE public.groups SET member_count = member_count + 1 WHERE id = NEW.group_id;
        RETURN NEW;
    ELSIF TG_OP = 'DELETE' THEN
        UPDATE public.groups SET member_count = GREATEST(member_count - 1, 0) WHERE id = OLD.group_id;
        RETURN OLD;
    END IF;
    RETURN NULL;
END;
$$;

CREATE TRIGGER group_members_maintain_count
AFTER INSERT OR DELETE ON public.group_members
FOR EACH ROW EXECUTE FUNCTION public.maintain_group_member_count();

CREATE OR REPLACE FUNCTION public.maintain_group_paper_vote_count()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public
AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        UPDATE public.group_papers SET vote_count = vote_count + 1 WHERE id = NEW.group_paper_id;
        RETURN NEW;
    ELSIF TG_OP = 'DELETE' THEN
        UPDATE public.group_papers SET vote_count = GREATEST(vote_count - 1, 0) WHERE id = OLD.group_paper_id;
        RETURN OLD;
    END IF;
    RETURN NULL;
END;
$$;

CREATE TRIGGER group_paper_votes_maintain_count
AFTER INSERT OR DELETE ON public.group_paper_votes
FOR EACH ROW EXECUTE FUNCTION public.maintain_group_paper_vote_count();

-- Prevent client-provided aggregate values when creating a club.
CREATE OR REPLACE FUNCTION public.reset_group_member_count()
RETURNS TRIGGER
LANGUAGE plpgsql
SET search_path = pg_catalog, public
AS $$
BEGIN
    NEW.member_count := 0;
    RETURN NEW;
END;
$$;

CREATE TRIGGER groups_reset_member_count
BEFORE INSERT ON public.groups
FOR EACH ROW EXECUTE FUNCTION public.reset_group_member_count();

-- -----------------------------------------------------------------------------
-- RLS helper functions. SECURITY DEFINER avoids recursive membership policies;
-- all object names are schema-qualified and search_path is fixed.
-- -----------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION public.is_group_member(p_group_id UUID)
RETURNS BOOLEAN
LANGUAGE SQL
STABLE
SECURITY DEFINER
SET search_path = pg_catalog, public
AS $$
    SELECT auth.uid() IS NOT NULL AND EXISTS (
        SELECT 1
        FROM public.group_members AS gm
        WHERE gm.group_id = p_group_id AND gm.user_id = auth.uid()
    );
$$;

CREATE OR REPLACE FUNCTION public.is_group_owner(p_group_id UUID)
RETURNS BOOLEAN
LANGUAGE SQL
STABLE
SECURITY DEFINER
SET search_path = pg_catalog, public
AS $$
    SELECT auth.uid() IS NOT NULL AND EXISTS (
        SELECT 1 FROM public.groups AS g
        WHERE g.id = p_group_id AND g.owner_id = auth.uid()
    );
$$;

CREATE OR REPLACE FUNCTION public.is_group_admin(p_group_id UUID)
RETURNS BOOLEAN
LANGUAGE SQL
STABLE
SECURITY DEFINER
SET search_path = pg_catalog, public
AS $$
    SELECT public.is_group_owner(p_group_id) OR EXISTS (
        SELECT 1 FROM public.group_members AS gm
        WHERE gm.group_id = p_group_id
          AND gm.user_id = auth.uid()
          AND gm.role = 'admin'
    );
$$;

CREATE OR REPLACE FUNCTION public.is_group_moderator(p_group_id UUID)
RETURNS BOOLEAN
LANGUAGE SQL
STABLE
SECURITY DEFINER
SET search_path = pg_catalog, public
AS $$
    SELECT public.is_group_admin(p_group_id) OR EXISTS (
        SELECT 1 FROM public.group_members AS gm
        WHERE gm.group_id = p_group_id
          AND gm.user_id = auth.uid()
          AND gm.role = 'moderator'
    );
$$;

CREATE OR REPLACE FUNCTION public.can_read_group(p_group_id UUID)
RETURNS BOOLEAN
LANGUAGE SQL
STABLE
SECURITY DEFINER
SET search_path = pg_catalog, public
AS $$
    SELECT auth.uid() IS NOT NULL AND EXISTS (
        SELECT 1 FROM public.groups AS g
        WHERE g.id = p_group_id
                    AND (
                            (g.status = 'active' AND g.visibility = 'public')
                            OR g.owner_id = auth.uid()
                            OR public.is_group_member(g.id)
                    )
    );
$$;

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

-- -----------------------------------------------------------------------------
-- Narrow privileged RPCs for private invitations and high-impact role actions
-- -----------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION public.create_group(
    p_name TEXT,
    p_description TEXT DEFAULT NULL,
    p_focus_area TEXT DEFAULT NULL,
    p_visibility TEXT DEFAULT 'private'
)
RETURNS SETOF public.groups
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public, extensions, auth
AS $$
DECLARE
    v_display_id TEXT;
    v_group public.groups%ROWTYPE;
BEGIN
    IF auth.uid() IS NULL THEN
        RAISE EXCEPTION 'Authentication required' USING ERRCODE = '42501';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM public.profiles AS p WHERE p.id = auth.uid()) THEN
        RAISE EXCEPTION 'Create your profile before creating a club' USING ERRCODE = '23503';
    END IF;
    IF p_visibility NOT IN ('private', 'public') THEN
        RAISE EXCEPTION 'Invalid club visibility' USING ERRCODE = '22023';
    END IF;

    LOOP
        v_display_id := upper(substr(encode(extensions.gen_random_bytes(8), 'hex'), 1, 8));
        EXIT WHEN NOT EXISTS (SELECT 1 FROM public.groups AS g WHERE g.display_id = v_display_id);
    END LOOP;

    INSERT INTO public.groups (display_id, name, description, owner_id, focus_area, visibility)
    VALUES (v_display_id, btrim(p_name), NULLIF(btrim(p_description), ''), auth.uid(), NULLIF(btrim(p_focus_area), ''), p_visibility)
    RETURNING * INTO v_group;

    INSERT INTO public.group_members (group_id, user_id, role)
    VALUES (v_group.id, auth.uid(), 'admin');

    SELECT * INTO v_group FROM public.groups AS g WHERE g.id = v_group.id;
    RETURN NEXT v_group;
END;
$$;

CREATE OR REPLACE FUNCTION public.leave_group(p_group_id UUID)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public, auth
AS $$
DECLARE
    v_owner_id UUID;
BEGIN
    IF auth.uid() IS NULL THEN
        RAISE EXCEPTION 'Authentication required' USING ERRCODE = '42501';
    END IF;
    SELECT g.owner_id INTO v_owner_id
    FROM public.groups AS g
    WHERE g.id = p_group_id AND g.status = 'active';
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Active club not found' USING ERRCODE = 'P0002';
    END IF;
    IF v_owner_id = auth.uid() THEN
        RAISE EXCEPTION 'Transfer ownership before leaving this club' USING ERRCODE = '22023';
    END IF;

    DELETE FROM public.group_members
    WHERE group_id = p_group_id AND user_id = auth.uid();
    IF NOT FOUND THEN
        RAISE EXCEPTION 'You are not a member of this club' USING ERRCODE = 'P0002';
    END IF;
END;
$$;

CREATE OR REPLACE FUNCTION public.create_group_invite(
    p_group_id UUID,
    p_expires_at TIMESTAMPTZ DEFAULT NOW() + INTERVAL '7 days',
    p_max_uses INTEGER DEFAULT 1
)
RETURNS TEXT
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public, extensions, auth
AS $$
DECLARE
    v_token TEXT;
BEGIN
    IF auth.uid() IS NULL OR NOT public.is_group_admin(p_group_id) THEN
        RAISE EXCEPTION 'Not authorized to create an invite' USING ERRCODE = '42501';
    END IF;
    IF p_expires_at <= NOW() OR p_expires_at > NOW() + INTERVAL '90 days' THEN
        RAISE EXCEPTION 'Invite expiry must be within the next 90 days' USING ERRCODE = '22023';
    END IF;
    IF p_max_uses < 1 OR p_max_uses > 500 THEN
        RAISE EXCEPTION 'Invite max uses must be between 1 and 500' USING ERRCODE = '22023';
    END IF;

    v_token := encode(extensions.gen_random_bytes(32), 'hex');
    INSERT INTO public.group_invites (group_id, token_hash, created_by, expires_at, max_uses)
    VALUES (p_group_id, extensions.digest(convert_to(v_token, 'UTF8'), 'sha256'), auth.uid(), p_expires_at, p_max_uses);
    RETURN v_token;
END;
$$;

CREATE OR REPLACE FUNCTION public.accept_group_invite(p_token TEXT)
RETURNS UUID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public, extensions, auth
AS $$
DECLARE
    v_invite public.group_invites%ROWTYPE;
    v_inserted INTEGER;
BEGIN
    IF auth.uid() IS NULL THEN
        RAISE EXCEPTION 'Authentication required' USING ERRCODE = '42501';
    END IF;
    IF p_token IS NULL OR p_token !~ '^[0-9a-fA-F]{64}$' THEN
        RAISE EXCEPTION 'Invalid invite' USING ERRCODE = '22023';
    END IF;

    SELECT * INTO v_invite
    FROM public.group_invites AS gi
    WHERE gi.token_hash = extensions.digest(convert_to(lower(p_token), 'UTF8'), 'sha256')
    FOR UPDATE;

    IF NOT FOUND
       OR v_invite.revoked_at IS NOT NULL
       OR v_invite.expires_at <= NOW()
       OR v_invite.uses_count >= v_invite.max_uses THEN
        RAISE EXCEPTION 'Invite is invalid, expired, revoked, or already used' USING ERRCODE = '22023';
    END IF;

    IF EXISTS (
        SELECT 1 FROM public.group_members AS gm
        WHERE gm.group_id = v_invite.group_id AND gm.user_id = auth.uid()
    ) THEN
        RETURN v_invite.group_id;
    END IF;

    INSERT INTO public.group_members (group_id, user_id, role)
    SELECT v_invite.group_id, auth.uid(), 'member'
    WHERE EXISTS (
        SELECT 1 FROM public.groups AS g
        WHERE g.id = v_invite.group_id AND g.status = 'active'
    )
    ON CONFLICT (group_id, user_id) DO NOTHING;

    GET DIAGNOSTICS v_inserted = ROW_COUNT;
    IF v_inserted = 0 THEN
        RAISE EXCEPTION 'Club is not active or membership could not be created' USING ERRCODE = '23514';
    END IF;

    UPDATE public.group_invites
    SET uses_count = uses_count + 1
    WHERE id = v_invite.id;
    RETURN v_invite.group_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.set_group_member_role(
    p_group_id UUID,
    p_user_id UUID,
    p_new_role TEXT
)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public, auth
AS $$
DECLARE
    v_owner_id UUID;
    v_actor_is_owner BOOLEAN;
    v_old_role TEXT;
BEGIN
    SELECT g.owner_id INTO v_owner_id
    FROM public.groups AS g
    WHERE g.id = p_group_id AND g.status = 'active'
    FOR UPDATE;
    IF NOT FOUND OR auth.uid() IS NULL THEN
        RAISE EXCEPTION 'Club not found or authentication required' USING ERRCODE = '42501';
    END IF;

    v_actor_is_owner := (v_owner_id = auth.uid());
    IF NOT v_actor_is_owner AND NOT public.is_group_admin(p_group_id) THEN
        RAISE EXCEPTION 'Not authorized to manage members' USING ERRCODE = '42501';
    END IF;
    IF p_new_role NOT IN ('admin', 'moderator', 'member') THEN
        RAISE EXCEPTION 'Invalid member role' USING ERRCODE = '22023';
    END IF;
    IF p_user_id = v_owner_id THEN
        RAISE EXCEPTION 'The owner role is managed by ownership transfer' USING ERRCODE = '22023';
    END IF;

    SELECT gm.role INTO v_old_role
    FROM public.group_members AS gm
    WHERE gm.group_id = p_group_id AND gm.user_id = p_user_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Member not found' USING ERRCODE = 'P0002';
    END IF;
    IF NOT v_actor_is_owner AND (p_new_role = 'admin' OR v_old_role = 'admin') THEN
        RAISE EXCEPTION 'Only the owner can grant or revoke admin role' USING ERRCODE = '42501';
    END IF;

    UPDATE public.group_members
    SET role = p_new_role
    WHERE group_id = p_group_id AND user_id = p_user_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.remove_group_member(p_group_id UUID, p_user_id UUID)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public, auth
AS $$
DECLARE
    v_owner_id UUID;
    v_target_role TEXT;
BEGIN
    SELECT g.owner_id INTO v_owner_id
    FROM public.groups AS g
    WHERE g.id = p_group_id
    FOR UPDATE;
    IF NOT FOUND OR auth.uid() IS NULL OR NOT public.is_group_admin(p_group_id) THEN
        RAISE EXCEPTION 'Not authorized to remove members' USING ERRCODE = '42501';
    END IF;
    IF p_user_id = v_owner_id THEN
        RAISE EXCEPTION 'The owner cannot be removed' USING ERRCODE = '22023';
    END IF;

    SELECT gm.role INTO v_target_role
    FROM public.group_members AS gm
    WHERE gm.group_id = p_group_id AND gm.user_id = p_user_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Member not found' USING ERRCODE = 'P0002';
    END IF;
    IF v_target_role = 'admin' AND auth.uid() <> v_owner_id THEN
        RAISE EXCEPTION 'Only the owner can remove an admin' USING ERRCODE = '42501';
    END IF;

    DELETE FROM public.group_members
    WHERE group_id = p_group_id AND user_id = p_user_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.transfer_group_ownership(p_group_id UUID, p_new_owner_id UUID)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public, auth
AS $$
DECLARE
    v_old_owner_id UUID;
BEGIN
    SELECT g.owner_id INTO v_old_owner_id
    FROM public.groups AS g
    WHERE g.id = p_group_id AND g.status = 'active'
    FOR UPDATE;
    IF NOT FOUND OR auth.uid() IS DISTINCT FROM v_old_owner_id THEN
        RAISE EXCEPTION 'Only the current owner can transfer ownership' USING ERRCODE = '42501';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM public.group_members AS gm
        WHERE gm.group_id = p_group_id AND gm.user_id = p_new_owner_id
    ) THEN
        RAISE EXCEPTION 'New owner must already be a club member' USING ERRCODE = '22023';
    END IF;

    UPDATE public.groups SET owner_id = p_new_owner_id WHERE id = p_group_id;
    UPDATE public.group_members
    SET role = 'admin'
    WHERE group_id = p_group_id AND user_id = v_old_owner_id;
    UPDATE public.group_members
    SET role = 'admin'
    WHERE group_id = p_group_id AND user_id = p_new_owner_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.archive_group(p_group_id UUID)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public, auth
AS $$
BEGIN
    UPDATE public.groups
    SET status = 'archived'
    WHERE id = p_group_id AND owner_id = auth.uid() AND status = 'active';
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Only the owner can archive an active club' USING ERRCODE = '42501';
    END IF;
END;
$$;

CREATE OR REPLACE FUNCTION public.check_in_session(p_presentation_id UUID)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public, auth
AS $$
DECLARE
    v_group_id UUID;
    v_status TEXT;
BEGIN
    IF auth.uid() IS NULL THEN
        RAISE EXCEPTION 'Authentication required' USING ERRCODE = '42501';
    END IF;
    SELECT p.group_id, p.status INTO v_group_id, v_status
    FROM public.group_presentations AS p
    WHERE p.id = p_presentation_id;
    IF NOT FOUND OR v_status = 'cancelled' OR NOT public.is_group_member(v_group_id) THEN
        RAISE EXCEPTION 'Session not found or membership required' USING ERRCODE = '42501';
    END IF;

    INSERT INTO public.group_session_attendance (presentation_id, user_id)
    VALUES (p_presentation_id, auth.uid())
    ON CONFLICT (presentation_id, user_id) DO NOTHING;
END;
$$;

CREATE OR REPLACE FUNCTION public.set_group_post_pinned(p_post_id UUID, p_is_pinned BOOLEAN)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public, auth
AS $$
DECLARE
    v_group_id UUID;
BEGIN
    SELECT p.group_id INTO v_group_id FROM public.group_posts AS p WHERE p.id = p_post_id;
    IF NOT FOUND OR auth.uid() IS NULL OR NOT public.is_group_moderator(v_group_id) THEN
        RAISE EXCEPTION 'Not authorized to pin club posts' USING ERRCODE = '42501';
    END IF;
    UPDATE public.group_posts SET is_pinned = p_is_pinned WHERE id = p_post_id;
END;
$$;

-- -----------------------------------------------------------------------------
-- Row-Level Security
-- -----------------------------------------------------------------------------

ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.keywords ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_preferences ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.saved_papers ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.groups ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.group_members ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.group_invites ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.group_papers ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.group_paper_votes ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.group_presentations ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.group_presentation_papers ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.group_session_rsvps ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.group_session_attendance ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.group_session_reviews ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.group_posts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.group_post_comments ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.group_post_reactions ENABLE ROW LEVEL SECURITY;

CREATE POLICY profiles_select_own ON public.profiles
    FOR SELECT TO authenticated USING (id = auth.uid());
CREATE POLICY profiles_insert_own ON public.profiles
    FOR INSERT TO authenticated
    WITH CHECK (id = auth.uid() AND email = (auth.jwt() ->> 'email'));
CREATE POLICY profiles_update_own ON public.profiles
    FOR UPDATE TO authenticated
    USING (id = auth.uid())
    WITH CHECK (id = auth.uid() AND email = (auth.jwt() ->> 'email'));

CREATE POLICY keywords_read ON public.keywords
    FOR SELECT TO anon, authenticated USING (TRUE);

CREATE POLICY user_preferences_select_own ON public.user_preferences
    FOR SELECT TO authenticated USING (user_id = auth.uid());
CREATE POLICY user_preferences_insert_own ON public.user_preferences
    FOR INSERT TO authenticated WITH CHECK (user_id = auth.uid());
CREATE POLICY user_preferences_update_own ON public.user_preferences
    FOR UPDATE TO authenticated USING (user_id = auth.uid()) WITH CHECK (user_id = auth.uid());
CREATE POLICY user_preferences_delete_own ON public.user_preferences
    FOR DELETE TO authenticated USING (user_id = auth.uid());

CREATE POLICY saved_papers_select_own ON public.saved_papers
    FOR SELECT TO authenticated USING (user_id = auth.uid());
CREATE POLICY saved_papers_insert_own ON public.saved_papers
    FOR INSERT TO authenticated WITH CHECK (user_id = auth.uid());
CREATE POLICY saved_papers_update_own ON public.saved_papers
    FOR UPDATE TO authenticated USING (user_id = auth.uid()) WITH CHECK (user_id = auth.uid());
CREATE POLICY saved_papers_delete_own ON public.saved_papers
    FOR DELETE TO authenticated USING (user_id = auth.uid());

CREATE POLICY groups_select_visible ON public.groups
    FOR SELECT TO authenticated USING (public.can_read_group(id));
CREATE POLICY groups_update_admin ON public.groups
    FOR UPDATE TO authenticated
    USING (public.is_group_admin(id))
    WITH CHECK (public.is_group_admin(id));
CREATE POLICY group_members_select_same_group ON public.group_members
    FOR SELECT TO authenticated
    USING (user_id = auth.uid() OR public.is_group_member(group_id));
CREATE POLICY group_members_insert_public_join ON public.group_members
    FOR INSERT TO authenticated
    WITH CHECK (
        user_id = auth.uid()
        AND (
            role = 'member'
            AND EXISTS (
                SELECT 1 FROM public.groups AS g
                WHERE g.id = group_id AND g.visibility = 'public' AND g.status = 'active'
            )
        )
    );

-- Invite tokens are never directly readable or writable through PostgREST.
-- create_group_invite / accept_group_invite are the only supported token operations.

CREATE POLICY group_papers_select_member ON public.group_papers
    FOR SELECT TO authenticated USING (public.is_group_member(group_id));
CREATE POLICY group_papers_insert_member ON public.group_papers
    FOR INSERT TO authenticated
    WITH CHECK (added_by = auth.uid() AND public.is_group_member(group_id));
CREATE POLICY group_papers_delete_author_or_admin ON public.group_papers
    FOR DELETE TO authenticated
    USING (added_by = auth.uid() OR public.is_group_admin(group_id));

CREATE POLICY group_paper_votes_select_own ON public.group_paper_votes
    FOR SELECT TO authenticated USING (user_id = auth.uid());
CREATE POLICY group_paper_votes_insert_member_own ON public.group_paper_votes
    FOR INSERT TO authenticated
    WITH CHECK (
        user_id = auth.uid()
        AND EXISTS (
            SELECT 1 FROM public.group_papers AS gp
            WHERE gp.id = group_paper_id AND public.is_group_member(gp.group_id)
        )
    );
CREATE POLICY group_paper_votes_delete_own ON public.group_paper_votes
    FOR DELETE TO authenticated USING (user_id = auth.uid());

CREATE POLICY presentations_select_member ON public.group_presentations
    FOR SELECT TO authenticated USING (public.is_group_member(group_id));
CREATE POLICY presentations_insert_member ON public.group_presentations
    FOR INSERT TO authenticated
    WITH CHECK (presenter_id = auth.uid() AND public.is_group_member(group_id));
CREATE POLICY presentations_update_presenter_or_admin ON public.group_presentations
    FOR UPDATE TO authenticated
    USING (presenter_id = auth.uid() OR public.is_group_admin(group_id))
    WITH CHECK (public.is_group_member(group_id));
CREATE POLICY presentations_delete_presenter_or_admin ON public.group_presentations
    FOR DELETE TO authenticated
    USING (presenter_id = auth.uid() OR public.is_group_admin(group_id));

CREATE POLICY presentation_papers_select_member ON public.group_presentation_papers
    FOR SELECT TO authenticated USING (public.is_group_member(group_id));
CREATE POLICY presentation_papers_insert_member ON public.group_presentation_papers
    FOR INSERT TO authenticated WITH CHECK (public.is_group_member(group_id));
CREATE POLICY presentation_papers_delete_admin ON public.group_presentation_papers
    FOR DELETE TO authenticated USING (public.is_group_admin(group_id));

CREATE POLICY session_rsvps_select_self_or_admin ON public.group_session_rsvps
    FOR SELECT TO authenticated
    USING (
        user_id = auth.uid()
        OR EXISTS (
            SELECT 1 FROM public.group_presentations AS p
            WHERE p.id = presentation_id AND public.is_group_admin(p.group_id)
        )
    );
CREATE POLICY session_rsvps_insert_member_own ON public.group_session_rsvps
    FOR INSERT TO authenticated
    WITH CHECK (
        user_id = auth.uid()
        AND EXISTS (
            SELECT 1 FROM public.group_presentations AS p
            WHERE p.id = presentation_id AND public.is_group_member(p.group_id)
        )
    );
CREATE POLICY session_rsvps_update_own ON public.group_session_rsvps
    FOR UPDATE TO authenticated
    USING (user_id = auth.uid())
    WITH CHECK (user_id = auth.uid());
CREATE POLICY session_rsvps_delete_own ON public.group_session_rsvps
    FOR DELETE TO authenticated USING (user_id = auth.uid());

CREATE POLICY session_attendance_select_self_or_admin ON public.group_session_attendance
    FOR SELECT TO authenticated
    USING (
        user_id = auth.uid()
        OR EXISTS (
            SELECT 1 FROM public.group_presentations AS p
            WHERE p.id = presentation_id AND public.is_group_admin(p.group_id)
        )
    );
CREATE POLICY session_attendance_insert_member_own ON public.group_session_attendance
    FOR INSERT TO authenticated
    WITH CHECK (
        user_id = auth.uid()
        AND EXISTS (
            SELECT 1 FROM public.group_presentations AS p
            WHERE p.id = presentation_id AND public.is_group_member(p.group_id)
        )
    );

CREATE POLICY session_reviews_select_member ON public.group_session_reviews
    FOR SELECT TO authenticated USING (public.is_group_member(group_id));
CREATE POLICY session_reviews_insert_member_own ON public.group_session_reviews
    FOR INSERT TO authenticated
    WITH CHECK (reviewer_id = auth.uid() AND public.is_group_member(group_id));
CREATE POLICY session_reviews_update_author_or_admin ON public.group_session_reviews
    FOR UPDATE TO authenticated
    USING (reviewer_id = auth.uid() OR public.is_group_admin(group_id))
    WITH CHECK (public.is_group_member(group_id));
CREATE POLICY session_reviews_delete_author_or_admin ON public.group_session_reviews
    FOR DELETE TO authenticated
    USING (reviewer_id = auth.uid() OR public.is_group_admin(group_id));

CREATE POLICY group_posts_select_member ON public.group_posts
    FOR SELECT TO authenticated USING (public.is_group_member(group_id));
CREATE POLICY group_posts_insert_member ON public.group_posts
    FOR INSERT TO authenticated
    WITH CHECK (
        author_id = auth.uid()
        AND public.is_group_member(group_id)
        AND NOT is_pinned
        AND (post_type = 'discussion' OR public.is_group_moderator(group_id))
    );
CREATE POLICY group_posts_update_author ON public.group_posts
    FOR UPDATE TO authenticated
    USING (author_id = auth.uid() AND NOT is_pinned)
    WITH CHECK (author_id = auth.uid() AND public.is_group_member(group_id));
CREATE POLICY group_posts_delete_author_or_admin ON public.group_posts
    FOR DELETE TO authenticated
    USING (author_id = auth.uid() OR public.is_group_admin(group_id));

CREATE POLICY group_comments_select_member ON public.group_post_comments
    FOR SELECT TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM public.group_posts AS p
            WHERE p.id = post_id AND public.is_group_member(p.group_id)
        )
    );
CREATE POLICY group_comments_insert_member ON public.group_post_comments
    FOR INSERT TO authenticated
    WITH CHECK (
        author_id = auth.uid()
        AND EXISTS (
            SELECT 1 FROM public.group_posts AS p
            WHERE p.id = post_id AND public.is_group_member(p.group_id)
        )
    );
CREATE POLICY group_comments_update_author ON public.group_post_comments
    FOR UPDATE TO authenticated
    USING (author_id = auth.uid())
    WITH CHECK (
        author_id = auth.uid()
        AND EXISTS (
            SELECT 1 FROM public.group_posts AS p
            WHERE p.id = post_id AND public.is_group_member(p.group_id)
        )
    );
CREATE POLICY group_comments_delete_author_or_admin ON public.group_post_comments
    FOR DELETE TO authenticated
    USING (
        author_id = auth.uid()
        OR EXISTS (
            SELECT 1 FROM public.group_posts AS p
            WHERE p.id = post_id AND public.is_group_admin(p.group_id)
        )
    );

CREATE POLICY group_reactions_select_member ON public.group_post_reactions
    FOR SELECT TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM public.group_posts AS p
            WHERE p.id = post_id AND public.is_group_member(p.group_id)
        )
    );
CREATE POLICY group_reactions_insert_own ON public.group_post_reactions
    FOR INSERT TO authenticated
    WITH CHECK (
        user_id = auth.uid()
        AND EXISTS (
            SELECT 1 FROM public.group_posts AS p
            WHERE p.id = post_id AND public.is_group_member(p.group_id)
        )
    );
CREATE POLICY group_reactions_delete_own ON public.group_post_reactions
    FOR DELETE TO authenticated USING (user_id = auth.uid());

-- Keep policy helper functions out of the public PostgREST RPC surface.
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

-- -----------------------------------------------------------------------------
-- Least-privilege grants. RLS is still required and remains enabled above.
-- -----------------------------------------------------------------------------

GRANT USAGE ON SCHEMA public TO anon, authenticated;

REVOKE ALL ON TABLE
    public.profiles,
    public.keywords,
    public.user_preferences,
    public.saved_papers,
    public.groups,
    public.group_members,
    public.group_invites,
    public.group_papers,
    public.group_paper_votes,
    public.group_presentations,
    public.group_presentation_papers,
    public.group_session_rsvps,
    public.group_session_attendance,
    public.group_session_reviews,
    public.group_posts,
    public.group_post_comments,
    public.group_post_reactions
FROM anon, authenticated;

GRANT SELECT, INSERT, UPDATE ON public.profiles TO authenticated;
GRANT SELECT ON public.keywords TO anon, authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.user_preferences TO authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.saved_papers TO authenticated;

GRANT SELECT ON public.groups TO authenticated;
GRANT UPDATE (name, description, focus_area, visibility)
    ON public.groups TO authenticated;
GRANT SELECT ON public.group_members TO authenticated;
GRANT INSERT (group_id, user_id, role) ON public.group_members TO authenticated;
GRANT SELECT, DELETE ON public.group_papers TO authenticated;
GRANT INSERT (group_id, bibcode, added_by) ON public.group_papers TO authenticated;
GRANT SELECT, DELETE ON public.group_paper_votes TO authenticated;
GRANT INSERT (group_paper_id, user_id) ON public.group_paper_votes TO authenticated;

GRANT SELECT, DELETE ON public.group_presentations TO authenticated;
GRANT INSERT (group_id, bibcode, presenter_id, scheduled_at, title, ends_at, time_zone, agenda)
    ON public.group_presentations TO authenticated;
GRANT UPDATE (bibcode, title, scheduled_at, ends_at, time_zone, agenda, status)
    ON public.group_presentations TO authenticated;
GRANT SELECT, DELETE ON public.group_presentation_papers TO authenticated;
GRANT INSERT (group_id, presentation_id, group_paper_id)
    ON public.group_presentation_papers TO authenticated;
GRANT SELECT, DELETE ON public.group_session_rsvps TO authenticated;
GRANT INSERT (presentation_id, user_id, response) ON public.group_session_rsvps TO authenticated;
GRANT UPDATE (response) ON public.group_session_rsvps TO authenticated;
GRANT SELECT ON public.group_session_attendance TO authenticated;
GRANT SELECT, DELETE ON public.group_session_reviews TO authenticated;
GRANT INSERT (group_id, bibcode, reviewer_id, notes, rating)
    ON public.group_session_reviews TO authenticated;
GRANT UPDATE (notes, rating) ON public.group_session_reviews TO authenticated;

GRANT SELECT, DELETE ON public.group_posts TO authenticated;
GRANT INSERT (group_id, author_id, post_type, body) ON public.group_posts TO authenticated;
GRANT UPDATE (body) ON public.group_posts TO authenticated;
GRANT SELECT, DELETE ON public.group_post_comments TO authenticated;
GRANT INSERT (post_id, author_id, body) ON public.group_post_comments TO authenticated;
GRANT UPDATE (body) ON public.group_post_comments TO authenticated;
GRANT SELECT, DELETE ON public.group_post_reactions TO authenticated;
GRANT INSERT (post_id, user_id, reaction) ON public.group_post_reactions TO authenticated;

-- Revoke table-wide INSERT so generated IDs, timestamps, counts, and pin state stay server-owned.
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

REVOKE ALL ON public.group_invites FROM anon, authenticated;
REVOKE ALL ON FUNCTION public.maintain_group_member_count() FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.maintain_group_paper_vote_count() FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.reset_group_member_count() FROM PUBLIC, anon, authenticated;

REVOKE ALL ON FUNCTION public.is_group_member(UUID) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.is_group_owner(UUID) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.is_group_admin(UUID) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.is_group_moderator(UUID) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.can_read_group(UUID) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.is_group_member(UUID) FROM authenticated;
REVOKE ALL ON FUNCTION public.is_group_owner(UUID) FROM authenticated;
REVOKE ALL ON FUNCTION public.is_group_admin(UUID) FROM authenticated;
REVOKE ALL ON FUNCTION public.is_group_moderator(UUID) FROM authenticated;
REVOKE ALL ON FUNCTION public.can_read_group(UUID) FROM authenticated;

REVOKE ALL ON SCHEMA private FROM PUBLIC, anon;
GRANT USAGE ON SCHEMA private TO authenticated;
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

REVOKE ALL ON FUNCTION public.create_group(TEXT, TEXT, TEXT, TEXT) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.leave_group(UUID) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.check_in_session(UUID) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.create_group(TEXT, TEXT, TEXT, TEXT) TO authenticated;
GRANT EXECUTE ON FUNCTION public.leave_group(UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.check_in_session(UUID) TO authenticated;

REVOKE ALL ON FUNCTION public.create_group_invite(UUID, TIMESTAMPTZ, INTEGER) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.accept_group_invite(TEXT) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.set_group_member_role(UUID, UUID, TEXT) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.remove_group_member(UUID, UUID) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.transfer_group_ownership(UUID, UUID) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.archive_group(UUID) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION public.set_group_post_pinned(UUID, BOOLEAN) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.create_group_invite(UUID, TIMESTAMPTZ, INTEGER) TO authenticated;
GRANT EXECUTE ON FUNCTION public.accept_group_invite(TEXT) TO authenticated;
GRANT EXECUTE ON FUNCTION public.set_group_member_role(UUID, UUID, TEXT) TO authenticated;
GRANT EXECUTE ON FUNCTION public.remove_group_member(UUID, UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.transfer_group_ownership(UUID, UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.archive_group(UUID) TO authenticated;
GRANT EXECUTE ON FUNCTION public.set_group_post_pinned(UUID, BOOLEAN) TO authenticated;

-- -----------------------------------------------------------------------------
-- Default astronomy interests used by onboarding. Authenticated clients are read-only.
-- -----------------------------------------------------------------------------

INSERT INTO public.keywords (name, category) VALUES
    ('Exoplanets', 'Planetary Systems'),
    ('Black Holes', 'High Energy'),
    ('Dark Matter', 'Cosmology'),
    ('Dark Energy', 'Cosmology'),
    ('Gravitational Waves', 'High Energy'),
    ('JWST', 'Observatories'),
    ('Hubble', 'Observatories'),
    ('SETI', 'Planetary Systems'),
    ('Astrobiology', 'Planetary Systems'),
    ('Supernovae', 'Stellar Evolution'),
    ('Neutron Stars', 'Stellar Evolution'),
    ('Pulsars', 'Stellar Evolution'),
    ('Quasars', 'Extragalactic'),
    ('Blazars', 'Extragalactic'),
    ('Active Galactic Nuclei', 'Extragalactic'),
    ('Cosmic Microwave Background', 'Cosmology'),
    ('Reionization', 'Cosmology'),
    ('First Stars', 'Cosmology'),
    ('Galaxy Evolution', 'Extragalactic'),
    ('Milky Way', 'Galactic'),
    ('Local Group', 'Extragalactic'),
    ('Star Formation', 'Stellar Evolution'),
    ('Protoplanetary Disks', 'Planetary Systems'),
    ('Solar Physics', 'Stellar Physics'),
    ('Space Weather', 'Stellar Physics'),
    ('Gamma-Ray Bursts', 'High Energy'),
    ('Neutrino Astronomy', 'High Energy'),
    ('Multi-messenger Astronomy', 'High Energy'),
    ('Asteroseismology', 'Stellar Physics'),
    ('Gaia Mission', 'Observatories'),
    ('Exoplanet Atmospheres', 'Planetary Systems'),
    ('Habitability', 'Planetary Systems'),
    ('Brown Dwarfs', 'Stellar Evolution'),
    ('White Dwarfs', 'Stellar Evolution'),
    ('Magnetars', 'Stellar Evolution'),
    ('X-ray Binaries', 'High Energy'),
    ('Tidal Disruption Events', 'High Energy'),
    ('Gravitational Lensing', 'Cosmology'),
    ('Large Scale Structure', 'Cosmology'),
    ('Baryon Acoustic Oscillations', 'Cosmology'),
    ('Fast Radio Bursts', 'High Energy'),
    ('Interstellar Medium', 'Galactic'),
    ('Galactic Center', 'Galactic'),
    ('Cosmic Rays', 'High Energy'),
    ('Dark Nebulae', 'Galactic'),
    ('Planetary Nebulae', 'Stellar Evolution'),
    ('Open Clusters', 'Galactic'),
    ('Globular Clusters', 'Galactic'),
    ('HII Regions', 'Galactic'),
    ('Molecular Clouds', 'Galactic'),
    ('Astrochemistry', 'Interdisciplinary'),
    ('Astrometry', 'Observational Techniques'),
    ('Photometry', 'Observational Techniques'),
    ('Spectroscopy', 'Observational Techniques'),
    ('Interferometry', 'Observational Techniques'),
    ('Adaptive Optics', 'Observational Techniques'),
    ('VLBI', 'Observational Techniques'),
    ('ALMA', 'Observatories'),
    ('VLT', 'Observatories'),
    ('Keck', 'Observatories'),
    ('LSST', 'Observatories'),
    ('Euclid', 'Observatories'),
    ('Roman Space Telescope', 'Observatories'),
    ('Chandra', 'Observatories'),
    ('XMM-Newton', 'Observatories'),
    ('Fermi', 'Observatories'),
    ('Swift', 'Observatories'),
    ('IceCube', 'Observatories'),
    ('LIGO', 'Observatories'),
    ('Virgo', 'Observatories'),
    ('KAGRA', 'Observatories'),
    ('LISA', 'Observatories'),
    ('James Webb', 'Observatories'),
    ('Spitzer', 'Observatories'),
    ('Herschel', 'Observatories'),
    ('Planck', 'Observatories'),
    ('WMAP', 'Observatories'),
    ('COBE', 'Observatories'),
    ('Rosetta', 'Observatories'),
    ('Cassini', 'Observatories'),
    ('Juno', 'Observatories'),
    ('Mars Exploration', 'Planetary Systems'),
    ('Lunar Research', 'Planetary Systems'),
    ('Asteroids', 'Planetary Systems'),
    ('Comets', 'Planetary Systems'),
    ('Kuiper Belt', 'Planetary Systems'),
    ('Oort Cloud', 'Planetary Systems'),
    ('Orbital Mechanics', 'Astrodynamics'),
    ('N-body Simulations', 'Computational'),
    ('Relativistic Astrophysics', 'Theoretical'),
    ('Plasma Astrophysics', 'Theoretical'),
    ('Nucleosynthesis', 'Stellar Physics'),
    ('Big Bang Nucleosynthesis', 'Cosmology'),
    ('Stellar Atmospheres', 'Stellar Physics'),
    ('Stellar Interiors', 'Stellar Physics'),
    ('Solar Neutrinos', 'Stellar Physics'),
    ('Helioseismology', 'Stellar Physics'),
    ('Exomoons', 'Planetary Systems'),
    ('Hot Jupiters', 'Planetary Systems'),
    ('Super-Earths', 'Planetary Systems'),
    ('Earth-like Planets', 'Planetary Systems')
ON CONFLICT (name) DO NOTHING;

-- -----------------------------------------------------------------------------
-- RPC and security smoke-test queries (run after setup while authenticated users exist)
-- -----------------------------------------------------------------------------
-- RLS should be enabled for every public application table:
-- SELECT tablename, rowsecurity FROM pg_tables
-- WHERE schemaname = 'public' ORDER BY tablename;
--
-- Policies present:
-- SELECT tablename, policyname, cmd, roles FROM pg_policies
-- WHERE schemaname = 'public' ORDER BY tablename, policyname;
--
-- Do not test policies as the SQL Editor postgres role; it bypasses RLS. Exercise them
-- through authenticated client JWTs for outsider, member, moderator, admin, and owner.
--
-- Required Android RPC integration before the existing Groups UI will work with these
-- least-privilege policies:
-- create_group(p_name, p_description, p_focus_area, p_visibility)
-- create_group_invite(p_group_id, p_expires_at, p_max_uses)
-- accept_group_invite(p_token)
-- set_group_member_role(p_group_id, p_user_id, p_new_role)
-- remove_group_member(p_group_id, p_user_id)
-- leave_group(p_group_id), archive_group(p_group_id), transfer_group_ownership(...)
-- Enable Supabase Realtime broadcast for Journal Club tables
ALTER PUBLICATION supabase_realtime ADD TABLE 
    groups, 
    group_members, 
    group_papers, 
    group_paper_votes, 
    group_presentations, 
    group_session_reviews;

COMMIT;